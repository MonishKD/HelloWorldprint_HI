package com.fortunategoods.domain.order;

public class PlacedState extends BaseOrderState{
    //refuses the other states since it'll use the Base one which throws an error

    @Override 
    public OrderStatus getStatus(){
        return OrderStatus.PLACED;
    }

    @Override 
    public void accept(Order order){
        order.stateTransition(new AcceptedState());
    }

    @Override 
     public void reject(Order order){
        order.stateTransition(new RejectedState());
    }

    @Override 
     public void cancel(Order order){
        order.stateTransition(new CanceledState());
    }
}
