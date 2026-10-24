package com.fortunategoods.application.ord.orders.rejection;

import java.util.Locale;

import com.fortunategoods.domain.order.OrderStatus;

/*
    Class will be used to send as a response to the frontend
*/
public class RejectOrderResponse {
    private final String orderId;
    private final String status;
    private final String rejectReason;

    public RejectOrderResponse(String orderId, OrderStatus status, String rejectReason) {
        this.orderId = orderId;
        this.status = status.name().toLowerCase(Locale.ROOT);
        this.rejectReason = rejectReason;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getStatus() {
        return status;
    }

    public String getRejectReason() {
        return rejectReason;
    }
}
