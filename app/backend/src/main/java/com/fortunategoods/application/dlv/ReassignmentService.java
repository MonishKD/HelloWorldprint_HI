package com.fortunategoods.application.dlv;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fortunategoods.application.dlv.reassignment.ReassignOrderResponse;
import com.fortunategoods.application.dlv.reassignment.StalledOrderResponse;
import com.fortunategoods.domain.courier.Assignment;
import com.fortunategoods.domain.courier.CourierStatus;
import com.fortunategoods.domain.courier.DispatchQueue.WaitingOrder;
import com.fortunategoods.domain.courier.DlvModulePermissions;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderEvent;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.order.validation.OrderVerify;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.AssignmentRepository;
import com.fortunategoods.technical.persistence.CourierRepository;
import com.fortunategoods.technical.persistence.OrderEventRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;

public class ReassignmentService {
    private final OrderRepository orders;
    private final OrderEventRepository events;
    private final AssignmentRepository assignments;
    private final CourierRepository couriers;
    private final RestaurantRepository restaurants;
    private final UserRepository users;
    private final DispatchService dispatch;

    public ReassignmentService(OrderRepository orders, OrderEventRepository events, AssignmentRepository assignments, CourierRepository couriers, RestaurantRepository restaurants, UserRepository users, DispatchService dispatch) {
        this.orders = orders;
        this.events = events;
        this.assignments = assignments;
        this.couriers = couriers;
        this.restaurants = restaurants;
        this.users = users;
        this.dispatch = dispatch;
    }

    public List<StalledOrderResponse> stalledOrders(String operatorId, UserRole role) {
        // Verify permissions
        OrdModulePermissions.requireRole(role, DlvModulePermissions.viewStalledOrdersRoles(), "Only operators can view stalled orders");
        // Verify operator id format
        OrderVerify.requireId(operatorId, "Operator ID");

        List<StalledOrderResponse> result = new ArrayList<>();

        // Add all stalled orders and create frontend response
        for (Order order : orders.findStalledOrders()) {
            Restaurant restaurant = restaurants.findById(order.getRestaurantId());
            result.add(new StalledOrderResponse(order.getId(), restaurant == null ? null : restaurant.getName(), users.findCourierName(order.getCourierId())));
        }

        return List.copyOf(result);
    }

    @Transactional
    public ReassignOrderResponse reassignOrder(String operatorId, UserRole role, String orderId) {
        // Verify permissions
        OrdModulePermissions.requireRole(role, DlvModulePermissions.reassignOrderRoles(), "Only operators can reassign orders");
        // Validate operator and order id formats
        OrderVerify.requireId(operatorId, "Operator ID");
        OrderVerify.requireId(orderId, "Order ID");

        // Lock order so no updates are made during reassigning and find orders
        orders.lockOrder(orderId);
        Order order = orders.findById(orderId);

        if (order == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "Order not found");
        }

        // Fetch all orders in DQ and lock their states
        List<WaitingOrder> waiting = new ArrayList<>(orders.lockWaitingForDispatch());
        couriers.lockAll();

        // Find active assignment with order
        Assignment current = assignments.findActiveByOrder(orderId);
        if (current == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT,"Order does not have an active assignment");
        }

        // Lock current courier's status using assignment's courierId
        CourierStatus status = couriers.lockStatusById(current.getCourierId());
        if (status == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "Courier not found");
        }

        // Check order and courier status to verify reassignment
        current.requireReassignmentAllowed(order, status);
        OrderEvent ready = readyEvent(orderId);

        // Set assignment to reassigned and clear the old courier from the assignment
        if (!assignments.markReassigned(current.getId()) || !orders.clearCourierIfUnchanged(orderId, current.getCourierId())) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Order or assignment changed before reassignment was saved");
        }
        // Set order's courier to null
        order.setCourierId(null);

        // Add order to the DQ
        waiting.add(new WaitingOrder(order, ready.getTimestamp(), Long.parseLong(ready.getId())));

        // Try dispatching again
        DispatchService.DispatchResult result = dispatch.retryDispatch(waiting);
        String offeredCourierId = null;

        // If any couriers accepted the order (stop the loop), otherwise offeredCourierId will be null and it remains
        // in the dispatch queue
        for (Assignment offer : result.offers()) {
            if (orderId.equals(offer.getOrderId())) {
                offeredCourierId = offer.getCourierId();
                break;
            }
        }

        return new ReassignOrderResponse(orderId, offeredCourierId);
    }

    private OrderEvent readyEvent(String orderId) {
        List<OrderEvent> history = events.findByOrderId(orderId);

        if (history.isEmpty()) {
            throw new IllegalStateException("Order has no event history");
        }

        OrderEvent latest = history.get(history.size() - 1);

        if (latest.getNewStatus() != OrderStatus.READY) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Order event history changed before reassignment");
        }

        return latest;
    }
}
