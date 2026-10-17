package com.fortunategoods.domain.order.StateDesignPattern;
import com.fortunategoods.domain.order.OrderStatus;

public class CanceledState extends BaseOrderState {
     @Override 
    public OrderStatus getStatus(){
        return OrderStatus.CANCELLED;
    }
    //again, it doesnt go anywhere from here
}
