package com.fortunategoods.domain.courier;

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
