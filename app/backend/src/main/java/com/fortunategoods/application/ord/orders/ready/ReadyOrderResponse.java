package com.fortunategoods.application.ord.orders.ready;

import java.util.Locale;

import com.fortunategoods.domain.order.OrderStatus;
/*
    Class used to send a response to the frontend
*/
public class ReadyOrderResponse {
    private final String orderId;
    private final String status;
    private final String offeredCourierId;

    // Constructor to prevent existing code from failing
    public ReadyOrderResponse(String orderId, OrderStatus status) {
        this(orderId, status, null);
    }

    public ReadyOrderResponse(String orderId, OrderStatus status, String offeredCourierId) {
        this.orderId = orderId;
        this.status = status.name().toLowerCase(Locale.ROOT);
        this.offeredCourierId = offeredCourierId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getStatus() {
        return status;
    }

    public String getOfferedCourierId() {
        return offeredCourierId;
    }

    public String getDispatchStatus() {
        return offeredCourierId == null ? "waiting" : "offered";
    }
}
