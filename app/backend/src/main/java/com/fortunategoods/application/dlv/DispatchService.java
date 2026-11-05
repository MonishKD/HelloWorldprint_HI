package com.fortunategoods.application.dlv;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fortunategoods.application.dlv.queue.DispatchQueueEntry;
import com.fortunategoods.domain.courier.Assignment;
import com.fortunategoods.domain.courier.DispatchQueue;
import com.fortunategoods.domain.courier.DlvModulePermissions;
import com.fortunategoods.domain.courier.PotentialCourier;
import com.fortunategoods.domain.courier.NearestCourierRule;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.order.validation.OrderVerify;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.AssignmentRepository;
import com.fortunategoods.technical.persistence.CourierRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;

@Service
public class DispatchService {
    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    private final OrderRepository orders;
    private final RestaurantRepository restaurants;
    private final CourierRepository couriers;
    private final AssignmentRepository assignments;

    public DispatchService(OrderRepository orders, RestaurantRepository restaurants, CourierRepository couriers, AssignmentRepository assignments) {
        this.orders = orders;
        this.restaurants = restaurants;
        this.couriers = couriers;
        this.assignments = assignments;
    }

    // Method that assigns a READY order to a courier
    @Transactional(propagation = Propagation.MANDATORY)
    public Assignment offerReadyOrder(String orderId) {
        // Verify validity of orderId
        OrderVerify.requireId(orderId, "Order ID");
        orders.lockOrder(orderId); // Thread-lock it so nothing else makes changes to it

        // Find order from Repo
        Order order = orders.findById(orderId);

        if (order == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "Order not found");
        }

        if (order.getStatus() != OrderStatus.READY) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT,"Only ready orders can be offered to couriers");
        }

        // Check if the order has already been assigned
        Assignment existing = assignments.findActiveByOrder(orderId);
        // If it has, don't continue further
        if (existing != null) {
            return existing;
        }

        // Goes through the queue so this order can't take a courier ahead of orders that have waited longer.
        // Returns this order's offer, or null while it waits.
        DispatchResult result = retryDispatch();
        return result.offers().stream()
            .filter(offer -> offer.getOrderId().equals(orderId))
            .findFirst()
            .orElse(null);
    }

    /*
        Call it whenever a courier becomes idle (goes on duty,
        delivers, is freed by an operator cancel) or an order comes back to the queue (decline,
        withdrawn offer, reassignment), inside that action's transaction.
        Offers the oldest waiting order first, each to the nearest courier still idle, one order per courier.
    */
    @Transactional(propagation = Propagation.MANDATORY)
    public DispatchResult retryDispatch() {
        // Locks the courier rows, so two retries running at once can't offer the same courier twice.
        List<PotentialCourier> idle = new ArrayList<>(couriers.findIdleWithoutOffer());
        DispatchQueue queue = new DispatchQueue(orders.findWaitingForDispatch());
        List<Assignment> offers = new ArrayList<>();

        while (!queue.isEmpty() && !idle.isEmpty()) {
            Order order = queue.takeOldest().order();
            Restaurant restaurant = restaurants.findById(order.getRestaurantId());
            if (restaurant == null) {
                throw new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "Restaurant not found");
            }

            PotentialCourier courier = NearestCourierRule.select(idle, restaurant.getLatitude(), restaurant.getLongitude());
            offers.add(assignments.add(Assignment.offered(order.getId(), courier.getId())));
            idle.remove(courier);
            log.info("Order {} offered to courier {}.", order.getId(), courier.getId());
        }

        List<DispatchQueue.WaitingOrder> stillWaiting = queue.snapshot();
        if (!stillWaiting.isEmpty()) {
            log.info("{} order(s) waiting in the dispatch queue for an idle courier.", stillWaiting.size());
        }
        return new DispatchResult(offers, stillWaiting);
    }

    // Oldest first. Throws FORBIDDEN for any role but operator. Read only: nothing is offered here.
    public List<DispatchQueueEntry> dispatchQueue(UserRole role) {
        OrdModulePermissions.requireRole(role, DlvModulePermissions.viewDispatchQueueRoles(), "Only operators can see the dispatch queue");

        DispatchQueue queue = new DispatchQueue(orders.findWaitingForDispatch());
        Map<String, String> restaurantNames = new HashMap<>();
        List<DispatchQueueEntry> entries = new ArrayList<>();
        for (DispatchQueue.WaitingOrder waiting : queue.snapshot()) {
            String restaurantName = restaurantNames.computeIfAbsent(waiting.order().getRestaurantId(), id -> {
                Restaurant restaurant = restaurants.findById(id);
                return restaurant == null ? null : restaurant.getName();
            });
            entries.add(new DispatchQueueEntry(waiting.order().getId(), restaurantName, waiting.readySince()));
        }
        return entries;
    }

    // stillWaiting is oldest first: index 0 is position 1 in the dispatch queue.
    public record DispatchResult(List<Assignment> offers, List<DispatchQueue.WaitingOrder> stillWaiting) {}
}
