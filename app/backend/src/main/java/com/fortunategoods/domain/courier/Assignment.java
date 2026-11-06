package com.fortunategoods.domain.courier;

import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderStatus;
/*
    Assignment class represents the link between an Order and a Courier
*/
public class Assignment {
    // Internal Status enum to help out in backend logic
    public enum Status {
        OFFERED,
        ACCEPTED,
        DECLINED,
        WITHDRAWN,
        REASSIGNED,
        CANCELLED
    }
    private final String id;
    private final String orderId;
    private final String courierId;
    private final Status status;
    private final boolean pickedUp;
    private final boolean delivered;

    public Assignment(String id, String orderId, String courierId, Status status, boolean pickedUp, boolean delivered) {
        this.id = id;
        this.orderId = orderId;
        this.courierId = courierId;
        this.status = status;
        this.pickedUp = pickedUp;
        this.delivered = delivered;
    }

    // Constructor helper for orders that were OFFERED to couriers
    public static Assignment offered(String orderId, String courierId) {
        return new Assignment(null, orderId, courierId, Status.OFFERED, false, false);
    }

    // Method that checks if assignment is accepted and not picked up and not delivered for pickup
    public void requirePickupAllowed(String actingCourierId) {
        requireOwner(actingCourierId);

        if (status != Status.ACCEPTED || pickedUp || delivered) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT,"Pickup requires an accepted assignment that has not been picked up or delivered");
        }
    }

    // Method that checks if assignment is accepted and not picked up and not delivered for delivery
    public void requireDeliveryAllowed(String actingCourierId) {
        requireOwner(actingCourierId);

        if (status != Status.ACCEPTED || !pickedUp || delivered) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Delivery requires an accepted assignment that has been picked up and not delivered");
        }
    }

    // Method that checks if order is ready, assignment accepted, courier offline, not picked up and not delivered
    public void requireReassignmentAllowed(Order order, CourierStatus courierStatus) {
        requireOrder(order);

        if (status != Status.ACCEPTED || pickedUp || delivered || order.getStatus() != OrderStatus.READY || courierStatus != CourierStatus.OFFLINE) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Only an order accepted by an offline courier and not yet picked up can be reassigned");
        }
    }
    
    // Method to check if order is still under this assignment/assigned to courier
    public void requireOrder(Order order) {
        if (order == null || orderId == null || courierId == null || !orderId.equals(order.getId()) || !courierId.equals(order.getCourierId())) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Order is no longer linked to this assignment and courier");
        }
    }

    // Helper method to check if courier picking up/delivering order is the one from the assignment
    private void requireOwner(String actingCourierId){
        if (courierId == null || !courierId.equals(actingCourierId)){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN, "Only allowed to confirm own assignments.");
        }
    }

    // Courier status handling after cancellation
    public CourierStatus courierStatusAfterCancellation(Order order, CourierStatus courierStatus) {
        if (order == null || orderId == null || courierId == null || !orderId.equals(order.getId()) || order.getStatus() != OrderStatus.CANCELLED || (status != Status.OFFERED && status != Status.ACCEPTED) || pickedUp || delivered) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Only an active assignment before pickup can be cancelled");
        }

        if (status == Status.ACCEPTED) {
            requireOrder(order);
            if (courierStatus != CourierStatus.ASSIGNED && courierStatus != CourierStatus.OFFLINE) {
                throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Accepted assignment requires an assigned or offline courier");
            }
        } else if (order.getCourierId() != null || (courierStatus != CourierStatus.IDLE && courierStatus != CourierStatus.OFFLINE)) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Offered assignment requires an unassigned order and an idle or offline courier");
        }

        return courierStatus == CourierStatus.ASSIGNED ? CourierStatus.IDLE : courierStatus;
    }

    public String getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCourierId() {
        return courierId;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isPickedUp() {
        return pickedUp;
    }

    public boolean isDelivered() {
        return delivered;
    }

}
