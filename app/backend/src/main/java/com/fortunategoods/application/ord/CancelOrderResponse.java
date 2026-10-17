package com.fortunategoods.application.ord;

import java.util.Locale;

import com.fortunategoods.domain.order.OrderStatus;

/*
    Class used to send response to frontend
*/
public class CancelOrderResponse {
    private final String orderId;
    private final String status;

    public CancelOrderResponse(String orderId, OrderStatus status) {
        this.orderId = orderId;
        this.status = status.name().toLowerCase(Locale.ROOT);
    }

    public String getOrderId() {
        return orderId;
    }

    public String getStatus() {
        return status;
    }
}
