package com.fortunategoods.application.dlv;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fortunategoods.application.ord.orders.OrderServiceResult;
import com.fortunategoods.domain.courier.Assignment;
import com.fortunategoods.domain.courier.CourierStatus;
import com.fortunategoods.domain.courier.DispatchQueue.WaitingOrder;
import com.fortunategoods.domain.courier.DlvModulePermissions;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderEvent;
import com.fortunategoods.domain.order.OrderFinished;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.order.validation.OrderVerify;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.AssignmentRepository;
import com.fortunategoods.technical.persistence.CourierRepository;
import com.fortunategoods.technical.persistence.OrderEventRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;

@Service
public class DeliveryService {
    private final OrderRepository orders;
    private final OrderEventRepository events;
    private final AssignmentRepository assignments;
    private final CourierRepository couriers;
    private final RestaurantRepository restaurants;
    private final DispatchService dispatch;
    private final ApplicationEventPublisher publisher;

    public DeliveryService(OrderRepository orders, OrderEventRepository events, AssignmentRepository assignments, CourierRepository couriers, RestaurantRepository restaurants, DispatchService dispatch, ApplicationEventPublisher publisher) {
        this.orders = orders;
        this.events = events;
        this.assignments = assignments;
        this.couriers = couriers;
        this.restaurants = restaurants;
        this.dispatch = dispatch;
        this.publisher = publisher;
    }

    @Transactional
    public OrderServiceResult confirmPickup(String courierId, UserRole role) {
        // Check if the person picking up is a courier
        OrdModulePermissions.requireRole(role, DlvModulePermissions.confirmPickupRoles(),"Only couriers can confirm pickup");
        // Check validity of courier's ID
        OrderVerify.requireId(courierId, "Courier ID");

        Assignment initial = findAssignment(courierId); // Find the assignment using courierID
        Order order = lockOrder(initial.getOrderId()); // Find the order using the assignment, and lock it so no updates are made to it
        CourierStatus status = courierStatus(courierId); // Find courier status from courier ID
        Assignment current = reloadAssignment(courierId, initial); // reload assignment to ensure no changes happened

        // Checks if pickup is allowed based on assignment and courier statuses
        current.requirePickupAllowed(courierId);
        current.requireOrder(order);
        status.requirePickupAllowed();

        // Use order's restaurant ID to fetch restaurant
        Restaurant restaurant = restaurants.findById(order.getRestaurantId());

        if (restaurant == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "Restaurant not found");
        }

        // Confirms state transition to picked_up
        order.pickUpFromReady();
        OrderServiceResult result = saveTransition(order, OrderStatus.READY);

        // Change assignment status and update courier's status and position to the restaurant's position
        if (!assignments.markPickedUp(current.getId()) || !couriers.updateStatusAndPosition(courierId, CourierStatus.ASSIGNED, CourierStatus.CARRYING, restaurant.getLatitude(), restaurant.getLongitude())) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Assignment or courier changed before pickup was saved");
        }

        return result;
    }

    @Transactional
    public OrderServiceResult confirmDelivery(String courierId, UserRole role) {
        // Check if the person picking up is a courier
        OrdModulePermissions.requireRole(role,DlvModulePermissions.confirmDeliveryRoles(), "Only couriers can confirm delivery");
        // Check validity of courier's ID
        OrderVerify.requireId(courierId, "Courier ID");

        Assignment initial = findAssignment(courierId); // find assignment from courier_id
        Order order = lockOrder(initial.getOrderId()); // Find the order using the assignment, and lock it so no updates are made to it

        List<WaitingOrder> waiting = orders.lockWaitingForDispatch(); // find a list of all orders in DQ
        couriers.lockAll(); // Lock all couriers info

        CourierStatus status = courierStatus(courierId); // get courier's current status
        Assignment current = reloadAssignment(courierId, initial); // reload assignment to ensure no changes happened

        // Checks if delivery has been picked up based on assignment and courier statuses
        current.requireDeliveryAllowed(courierId);
        current.requireOrder(order);
        status.requireDeliveryAllowed();

        // Confirms state transition to delivered
        order.deliverFromPickedUp();
        OrderServiceResult result = saveTransition(order, OrderStatus.PICKED_UP);

        // Get order's current location
        Double latitude = order.getDeliveryLatitude();
        Double longitude = order.getDeliveryLongitude();

        if (latitude == null || longitude == null) {
            latitude = null;
            longitude = null;
        }

        // Change assignment status to delivered and update courier's coordinates to that of the order
        if (!assignments.markDelivered(current.getId())|| !couriers.updateStatusAndPosition(courierId, CourierStatus.CARRYING, CourierStatus.IDLE, latitude, longitude)) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Assignment or courier changed before delivery was saved");
        }

        // Reload DQ to allow other orders to get assigned
        dispatch.retryDispatch(waiting);
        publisher.publishEvent(new OrderFinished(order.getId(), OrderStatus.DELIVERED));

        return result;
    }


    // Helper method to find an assignment from the assignment repo
    private Assignment findAssignment(String courierId) {
        Assignment assignment = assignments.findActiveByCourier(courierId);

        if (assignment == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "You do not have an active assignment");
        }

        return assignment;
    }

    // Helper method to lock an order to modify it
    private Order lockOrder(String orderId) {
        orders.lockOrder(orderId);
        Order order = orders.findById(orderId);

        if (order == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "Order not found");
        }

        return order;
    }

    // Helper method to get courier status 
    private CourierStatus courierStatus(String courierId) {
        CourierStatus status = couriers.lockStatusById(courierId);

        if (status == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "Courier not found");
        }

        return status;
    }

    // Helper method to reload an assignment
    private Assignment reloadAssignment(String courierId, Assignment initial) {
        Assignment current = findAssignment(courierId);

        if (!initial.getId().equals(current.getId()) || !initial.getOrderId().equals(current.getOrderId())) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Your assignment changed; refresh and try again");
        }

        return current;
    }

    private OrderServiceResult saveTransition(Order order, OrderStatus previous) {
        List<OrderEvent> history = events.findByOrderId(order.getId());

        if (history.isEmpty()) {
            throw new IllegalStateException("Order has no event history");
        }

        OrderEvent latest = history.get(history.size() - 1);

        if (latest.getNewStatus() != previous) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Order event history changed before the handoff");
        }

        String eventId = orders.updateStatusIfUnchanged(
            order.getId(), latest.getId(), previous, order.getStatus()
        );

        if (eventId == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Order changed before the handoff was saved");
        }

        return new OrderServiceResult(
            order.getId(), order.getStatus(), Long.parseLong(eventId)
        );
    }
}
