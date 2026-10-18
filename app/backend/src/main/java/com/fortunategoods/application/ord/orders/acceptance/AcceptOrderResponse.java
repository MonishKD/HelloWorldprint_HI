package com.fortunategoods.application.ord.orders.acceptance;

import java.util.Locale;
import com.fortunategoods.domain.order.OrderStatus;

public class AcceptOrderResponse {
    private final String orderId;
    private final String status;

    public AcceptOrderResponse(String orderId, OrderStatus status) {
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
