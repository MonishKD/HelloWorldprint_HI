package com.fortunategoods.application.prc;

import org.springframework.stereotype.Service;

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
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.order.validation.OrderVerify;
import com.fortunategoods.domain.pricing.LedgerEntry;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.LedgerRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;

// Assembles role-scoped order history and delivered-order summaries.
@Service
public class OrderHistoryService {
    private final LedgerRepository ledgerRepo;
    private final OrderRepository orderRepo;
    private final RestaurantRepository restaurantRepo;
    private final UserRepository userRepo;

    public OrderHistoryService(LedgerRepository ledgerRepo, OrderRepository orderRepo,
            RestaurantRepository restaurantRepo, UserRepository userRepo) {
        this.ledgerRepo = ledgerRepo;
        this.orderRepo = orderRepo;
        this.restaurantRepo = restaurantRepo;
        this.userRepo = userRepo;
    }

    /*
        Each role only gets its own ledger rows, newest first:
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
        Builds a delivered order's summary from its CHARGE ledger entry (the fare and the times)
        and the order itself (lines, address, card).
        A customer only reads their own orders, the operator any order. Who is asking comes from the session.
        Command-level role checks remain in GetOrderSummaryCommand.
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
            // The order line keeps dollars (29.99): rounded to cents the same way FareCalculator does, so it is exactly 2999
            long priceCents = Math.round(line.getPrice() * 100);
            lines.add(new OrderSummaryLineResponse(line.getName(), line.getQuantity(), priceCents));
        }

        Restaurant restaurant = restaurantRepo.findById(order.getRestaurantId());
        String restaurantName = restaurant == null ? null : restaurant.getName();

        return new OrderSummaryResponse(orderId, lines,
                charge.getSubtotalCents(), charge.getDeliveryFeeCents(),
                charge.getServiceFeeCents(), charge.getSurchargeCents(), charge.getAmountCents(),
                charge.getPlacedAt(), charge.getFinishedAt(), restaurantName,
                userRepo.findDeliveringCourierName(orderId), order.getDeliveryAddress(),
                orderRepo.findCardBrand(orderId), orderRepo.findCardLast4(orderId));
    }
}
