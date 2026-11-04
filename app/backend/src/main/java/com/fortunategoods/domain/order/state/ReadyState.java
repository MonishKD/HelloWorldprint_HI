package com.fortunategoods.domain.order.state;

import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.order.Order;

public class ReadyState extends BaseOrderState {

    @Override 
    public OrderStatus getStatus(){
        return OrderStatus.READY;
    }

    @Override
    public void cancel(Order order){
        order.transitionTo(new CanceledState());
    }
    
}
