package com.fortunategoods.domain.order;

import java.time.LocalDateTime;

public class OrderEvent {

    private String id;
    private String orderId;
    private String previousEventId;
    private OrderStatus previousStatus;
    private OrderStatus newStatus;
    private LocalDateTime timestamp;

    public OrderEvent(String id, String orderId, String previousEventId, OrderStatus previousStatus,
            OrderStatus newStatus, LocalDateTime timestamp) {
        this.id = id;
        this.orderId = orderId;
        this.previousEventId = previousEventId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getPreviousEventId() {
        return previousEventId;
    }

    public OrderStatus getPreviousStatus() {
        return previousStatus;
    }

    public OrderStatus getNewStatus() {
        return newStatus;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

}
