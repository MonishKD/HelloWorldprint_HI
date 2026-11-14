package com.fortunategoods.domain.order.state;

import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderStatus;

public class PickedUpState extends BaseOrderState {
    @Override 
    public OrderStatus getStatus(){
        return OrderStatus.PICKED_UP;
    }

    @Override 
    public void deliver(Order order){
        order.transitionTo(new DeliveredState());
    }
    
}
