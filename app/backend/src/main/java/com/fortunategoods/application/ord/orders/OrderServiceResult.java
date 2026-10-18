package com.fortunategoods.application.ord.orders;

import com.fortunategoods.domain.order.OrderStatus;

/*
    Class which wll be used to send a success message from OrderService to the concrete PlaceOrderCommand
*/
public class OrderServiceResult {
    private final String orderId;
    private final OrderStatus status;
    private final long eventId;
    private final String rejectReason;

    // Prevents existing code using it to fail
    public OrderServiceResult(String orderId, OrderStatus status, long eventId){
        this(orderId, status, eventId, null); // will call the constructor below
    }

    public OrderServiceResult(String orderId, OrderStatus status, long eventId, String rejectReason){
        this.orderId = orderId;
        this.status = status;
        this.eventId = eventId;
        this.rejectReason = rejectReason;
    }

    public String getOrderId() {
        return orderId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public long getEventId() {
        return eventId;
    }

    public String getRejectReason(){
        return rejectReason;
    }
}
