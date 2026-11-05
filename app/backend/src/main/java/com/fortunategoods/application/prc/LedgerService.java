package com.fortunategoods.application.prc;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fortunategoods.application.prc.history.CourierHistoryResponse;
import com.fortunategoods.application.prc.history.HistoryResponse;
import com.fortunategoods.application.prc.history.LedgerHistoryResponse;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderEvent;
import com.fortunategoods.domain.order.OrderFinished;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.pricing.Fare;
import com.fortunategoods.domain.pricing.FareCalculator;
import com.fortunategoods.domain.pricing.LedgerEntry;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.technical.persistence.OrderEventRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.LedgerRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;


// Writes one ledger entry when an order ends, and reads the ledger back for each role's history (#264).
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
}
