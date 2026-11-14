package com.fortunategoods.domain.order.state;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderStatus;


public interface OrderState {
    public OrderStatus getStatus();
    public void accept(Order order);
    public void startPreparing(Order order);
    public void markReady(Order order);
    public void reject(Order order, String reason);
    public void cancel(Order order);
    public void pickUp(Order order);
    public void deliver(Order order);
    public void refund(Order order);
}
