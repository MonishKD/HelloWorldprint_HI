package com.fortunategoods.domain.order.state;

import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderStatus;

public class DeliveredState extends BaseOrderState {
    @Override 
    public OrderStatus getStatus(){
        return OrderStatus.DELIVERED;
    }

    @Override 
    public void deliver(Order order){
        order.transitionTo(new RefundedState());
    }
    
}
