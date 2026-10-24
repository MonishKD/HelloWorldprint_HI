package com.fortunategoods.application.ord.orders.preparation;

import java.util.Locale;

import com.fortunategoods.domain.order.OrderStatus;
/*
    Class used to send a response to the frontend
*/
public class PrepareOrderResponse {
    private final String orderId;
    private final String status;

    public PrepareOrderResponse(String orderId, OrderStatus status) {
        this.orderId = orderId;
        if (status == null) {
            throw new IllegalArgumentException("OrderStatus cannot be null");
        }
        this.status = status.name().toLowerCase(Locale.ROOT);
    }

    public String getOrderId() {
        return orderId;
    }

    public String getStatus() {
        return status;
    }
}
