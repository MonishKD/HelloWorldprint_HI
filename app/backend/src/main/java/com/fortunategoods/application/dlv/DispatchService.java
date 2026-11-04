package com.fortunategoods.application.dlv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fortunategoods.domain.courier.Assignment;
import com.fortunategoods.domain.courier.PotentialCourier;
import com.fortunategoods.domain.courier.NearestCourierRule;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.order.validation.OrderVerify;
import com.fortunategoods.domain.restaurant.Restaurant;
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

        // Find restaurant using order's restaurant
        Restaurant restaurant = restaurants.findById(order.getRestaurantId());

        if (restaurant == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "Restaurant not found");
        }

        // Apply the nearest courier rule to check which idle courier is nearest
        PotentialCourier selected = NearestCourierRule.select(
            couriers.findIdleWithoutOffer(),
            restaurant.getLatitude(),
            restaurant.getLongitude()
        );

        if (selected == null) {
            log.info("Order {} is waiting for an idle courier.", orderId);
            return null;
        }

        // Create an assignment between courier and order
        Assignment offer = assignments.add(Assignment.offered(orderId, selected.getId()));
        log.info("Order {} offered to courier {}.", orderId, selected.getId());
        return offer;
    }
}
