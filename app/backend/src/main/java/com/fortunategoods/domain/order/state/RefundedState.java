package com.fortunategoods.domain.order.state;

import com.fortunategoods.domain.order.OrderStatus;

public class RefundedState extends BaseOrderState {
    @Override 
    public OrderStatus getStatus(){
        return OrderStatus.REFUNDED;
    }

    //wont go anywhere
    
}
