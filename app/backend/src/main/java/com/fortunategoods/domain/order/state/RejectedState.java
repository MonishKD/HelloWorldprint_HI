package com.fortunategoods.domain.order.state;
import com.fortunategoods.domain.order.OrderStatus;

public class RejectedState extends BaseOrderState{
    @Override 
    public OrderStatus getStatus(){
        return OrderStatus.REJECTED;
    }
    //also doesnt go anywhere from here
}
