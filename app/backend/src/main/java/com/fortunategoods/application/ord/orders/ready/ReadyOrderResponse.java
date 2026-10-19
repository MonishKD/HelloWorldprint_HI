package com.fortunategoods.application.ord.orders.ready;

import java.util.Locale;

import com.fortunategoods.domain.order.OrderStatus;
/*
    Class used to send a response to the frontend
*/
public class ReadyOrderResponse {
    private final String orderId;
    private final String status;

    public ReadyOrderResponse(String orderId, OrderStatus status) {
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
