package com.fortunategoods.domain.order.state;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderStatus;

public class AcceptedState extends BaseOrderState{

    @Override 
    public OrderStatus getStatus(){
        return OrderStatus.ACCEPTED;
    }

    @Override
    public void startPreparing(Order order){
        order.transitionTo(new PreparingState());
    }

    @Override
    public void cancel(Order order){
        order.transitionTo(new CanceledState());
    }
}
