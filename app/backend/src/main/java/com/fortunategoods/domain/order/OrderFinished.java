package com.fortunategoods.domain.order;

//activated when an order reaches a finishing status from ORderService, to be implemented
public class OrderFinished {
    private final String orderId;
    private final OrderStatus status;

    public OrderFinished(String orderId, OrderStatus status) {
        this.orderId = orderId;
        this.status = status;
    }

    public String getOrderId(){
        return orderId;
    }
    public OrderStatus getFinalStatus(){
        return status;
    }
}
