package com.fortunategoods.domain.order.StateDesignPattern;
import com.fortunategoods.domain.order.Order;

import com.fortunategoods.domain.order.OrderStatus;

public class PreparingState extends BaseOrderState {
    
    @Override 
    public OrderStatus getStatus(){
        return OrderStatus.PREPARING;
    }

    @Override
    public void markReady(Order order){
        order.transitionTo(new ReadyState());
    }

}
