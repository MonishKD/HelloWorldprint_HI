package com.fortunategoods.application.prc;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fortunategoods.application.prc.history.CourierHistoryResponse;
import com.fortunategoods.application.prc.history.HistoryResponse;
import com.fortunategoods.application.prc.history.LedgerHistoryResponse;
import com.fortunategoods.application.prc.summary.OrderSummaryLineResponse;
import com.fortunategoods.application.prc.summary.OrderSummaryResponse;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderEvent;
import com.fortunategoods.domain.order.OrderFinished;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.order.validation.OrderVerify;
import com.fortunategoods.domain.pricing.Fare;
import com.fortunategoods.domain.pricing.FareCalculator;
import com.fortunategoods.domain.pricing.LedgerEntry;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.OrderEventRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.LedgerRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;


// Writes one ledger entry when an order ends, and reads the ledger back for each role's history (#264)
// and for a delivered order's summary (#262).
// LedgerRepository (#236) stores the entries in ledger_entries.
@Service
public class LedgerService {
    private final LedgerRepository ledgerRepo;
    private final OrderRepository orderRepo;
    private final OrderEventRepository orderEventRepo;
    private final FareCalculator fareCalculator;
    private final RestaurantRepository restaurantRepo;
    private final UserRepository userRepo;

    public LedgerService(LedgerRepository ledgerRepo, OrderRepository orderRepo, OrderEventRepository orderEventRepo, FareCalculator fareCalculator,
            RestaurantRepository restaurantRepo, UserRepository userRepo) {
        this.ledgerRepo = ledgerRepo;
        this.orderRepo = orderRepo;
        this.orderEventRepo = orderEventRepo;
        this.fareCalculator = fareCalculator;
        this.restaurantRepo = restaurantRepo;
        this.userRepo = userRepo;
    }
    //https://www.baeldung.com/spring-events
    // Plain @EventListener: runs in the same transaction as the status change, so the entry and the change are saved together
    @EventListener
    public void onOrderFinished(OrderFinished event){
        if(event.getFinalStatus() == null){
            throw new IllegalStateException("OrderFinished has null end status");
        }

        // The order's events are oldest first: the first one is when it was placed, the last one is when it finished
        List<OrderEvent> events = orderEventRepo.findByOrderId(event.getOrderId());
        LocalDateTime placedAt = events.get(0).getTimestamp();
        LocalDateTime finishedAt = events.get(events.size() - 1).getTimestamp();

        if(event.getFinalStatus() == OrderStatus.DELIVERED){
            //get order to get order lines next
            Order order = orderRepo.findById(event.getOrderId());
            Fare fare = fareCalculator.calculate(order.getLines());
            String paymentReference = orderRepo.findPaymentId(event.getOrderId());
            ledgerRepo.save(LedgerEntry.charge(event.getOrderId(), fare, placedAt, finishedAt, paymentReference));
        }
        else if(event.getFinalStatus() == OrderStatus.CANCELLED || event.getFinalStatus() == OrderStatus.REJECTED ){
            ledgerRepo.save(LedgerEntry.noCharge(event.getOrderId(), event.getFinalStatus(), placedAt, finishedAt));
        }
    }

    /*
        Receiver of GetHistoryCommand (#264, R-PRC-04). Each role only gets its own ledger rows, newest first:
          CUSTOMER    the orders they placed
          RESTAURANT  the orders of the restaurant their account works for
          COURIER     the orders they delivered, without any money field
          OPERATOR    every row
        Who is asking always comes from the session, never from the request (like DashboardService).
    */
    public List<HistoryResponse> historyOf(Session session) {
        String userId = String.valueOf(session.getUserID());

        switch (session.getRole()) {
            case CUSTOMER:
                return toLedgerRows(ledgerRepo.findByCustomer(userId));
            case RESTAURANT:
                String restaurantId = userRepo.findRestaurantIdByUserId(userId);
                if (restaurantId == null) {
                    throw new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN, "Your account is not linked to a restaurant");
                }
                return toLedgerRows(ledgerRepo.findByRestaurant(restaurantId));
            case COURIER:
                return toCourierRows(ledgerRepo.findByCourier(userId));
            case OPERATOR:
                return toLedgerRows(ledgerRepo.findAll());
            default:
                throw new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN, "This role has no history");
        }
    }

    // Customer, restaurant and operator rows: the full ledger entry and the restaurant's name.
    private List<HistoryResponse> toLedgerRows(List<LedgerEntry> entries) {
        Map<String, String> restaurantNames = new HashMap<>(); // each restaurant is looked up once
        List<HistoryResponse> rows = new ArrayList<>();
        for (LedgerEntry entry : entries) {
            Order order = orderRepo.findById(entry.getOrderId());
            rows.add(new LedgerHistoryResponse(entry, restaurantName(order.getRestaurantId(), restaurantNames)));
        }
        return rows;
    }

    // Courier rows: only the CHARGE entry, which is written when the order is delivered.
    // A later REFUND of the same order is about money, so the courier doesn't get a second row for it.
    private List<HistoryResponse> toCourierRows(List<LedgerEntry> entries) {
        Map<String, String> restaurantNames = new HashMap<>();
        List<HistoryResponse> rows = new ArrayList<>();
        for (LedgerEntry entry : entries) {
            if (entry.getType() != LedgerEntry.Type.CHARGE) {
                continue;
            }
            Order order = orderRepo.findById(entry.getOrderId());
            String destinationName = userRepo.findCustomerName(order.getCustomerId());
            rows.add(new CourierHistoryResponse(entry.getOrderId(), restaurantName(order.getRestaurantId(), restaurantNames),
                    destinationName, entry.getFinishedAt()));
        }
        return rows;
    }

    // The restaurant's name, looked up once per restaurant. null when the restaurant no longer exists.
    private String restaurantName(String restaurantId, Map<String, String> restaurantNames) {
        if (!restaurantNames.containsKey(restaurantId)) {
            Restaurant restaurant = restaurantRepo.findById(restaurantId);
            restaurantNames.put(restaurantId, restaurant == null ? null : restaurant.getName());
        }
        return restaurantNames.get(restaurantId);
    }

    /*
        Receiver of GetOrderSummaryCommand (#262, R-PRC-02). Builds a delivered order's summary from
        its CHARGE ledger entry (the fare and the times) and the order itself (lines, address, card).
        A customer only reads their own orders, the operator any order. Who is asking comes from the session.
        Throws NOT_FOUND for an unknown order, FORBIDDEN for another customer's order,
        and CONFLICT before delivery (the CHARGE entry is only written when the order is delivered).
    */
    public OrderSummaryResponse summaryOf(Session session, String orderId) {
        OrderVerify.requireId(orderId, "Order ID"); // a non-numeric id is refused (400) instead of failing (500)
        Order order = orderRepo.findById(orderId);
        if (order == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "Order not found");
        }

        boolean isOwnOrder = String.valueOf(session.getUserID()).equals(order.getCustomerId());
        if (session.getRole() == UserRole.CUSTOMER && !isOwnOrder) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN, "You can only see the summary of your own orders");
        }

        LedgerEntry charge = ledgerRepo.findCharge(orderId);
        if (charge == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "The order summary is only available once the order is delivered");
        }

        List<OrderSummaryLineResponse> lines = new ArrayList<>();
        for (OrderLine line : order.getLines()) {
            BigDecimal price = BigDecimal.valueOf(line.getPrice()).setScale(2, RoundingMode.HALF_UP);
            lines.add(new OrderSummaryLineResponse(line.getName(), line.getQuantity(), price));
        }

        Restaurant restaurant = restaurantRepo.findById(order.getRestaurantId());
        String restaurantName = restaurant == null ? null : restaurant.getName();

        return new OrderSummaryResponse(orderId, lines,
                dollars(charge.getSubtotalCents()), dollars(charge.getDeliveryFeeCents()),
                dollars(charge.getServiceFeeCents()), dollars(charge.getSurchargeCents()), dollars(charge.getAmountCents()),
                charge.getPlacedAt(), charge.getFinishedAt(), restaurantName,
                userRepo.findDeliveringCourierName(orderId), order.getDeliveryAddress(),
                orderRepo.findCardBrand(orderId), orderRepo.findCardLast4(orderId));
    }

    // The ledger keeps cents, the summary shows dollars: 1250 cents -> 12.50
    private BigDecimal dollars(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }
}
