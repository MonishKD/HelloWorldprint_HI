package com.fortunategoods.application.ord.orders.preparation;

import java.util.Locale;

import com.fortunategoods.domain.order.OrderStatus;

public class GetPreparationQueueResponse {
    private final int position;
    private final String orderId;
    private final String status;

    public GetPreparationQueueResponse(int position, String orderId, OrderStatus status) {
        this.position = position;
        this.orderId = orderId;
        this.status = status.name().toLowerCase(Locale.ROOT);
    }

    public int getPosition() {
        return position;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getStatus() {
        return status;
    }
}
