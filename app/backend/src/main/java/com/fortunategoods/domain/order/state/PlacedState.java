package com.fortunategoods.domain.order.state;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderStatus;

public class PlacedState extends BaseOrderState{
    //refuses the other states since it'll use the Base one which throws an error

    @Override 
    public OrderStatus getStatus(){
        return OrderStatus.PLACED;
    }

    @Override 
    public void accept(Order order){
        order.transitionTo(new AcceptedState());
    }

    @Override 
     public void reject(Order order,String reason){
        order.transitionTo(new RejectedState());
    }

    @Override 
     public void cancel(Order order){
        order.transitionTo(new CanceledState());
    }
}
