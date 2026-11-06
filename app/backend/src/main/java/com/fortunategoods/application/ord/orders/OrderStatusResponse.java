package com.fortunategoods.application.ord.orders;

import java.util.Locale;

import com.fortunategoods.domain.order.OrderStatus;

// What a command that moves an order sends back: which order, and its new status in lowercase.
public record OrderStatusResponse(String orderId, String status) {
    public OrderStatusResponse(String orderId, OrderStatus status) {
        this(orderId, status.name().toLowerCase(Locale.ROOT));
    }
}
