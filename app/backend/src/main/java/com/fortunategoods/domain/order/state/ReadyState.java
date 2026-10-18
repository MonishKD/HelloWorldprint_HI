package com.fortunategoods.domain.order.state;

import com.fortunategoods.domain.order.OrderStatus;

public class ReadyState extends BaseOrderState {

    @Override 
    public OrderStatus getStatus(){
        return OrderStatus.READY;
    }

    //wont go anywhere from here
    
}
