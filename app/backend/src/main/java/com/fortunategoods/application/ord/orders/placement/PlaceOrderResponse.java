package com.fortunategoods.application.ord.orders.placement;

import java.util.Locale;

import com.fortunategoods.domain.order.OrderStatus;
/*
    Class used to send a response to the frontend
*/
public class PlaceOrderResponse {
    private final String orderId;
    private final String status;

    public PlaceOrderResponse(String orderId, OrderStatus status) {
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
