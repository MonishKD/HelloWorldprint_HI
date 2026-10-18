package com.fortunategoods.domain.order.state;
import com.fortunategoods.domain.order.Order;
//everything fail for now, no good explanation just IllegalTransitionExcp
public abstract class BaseOrderState implements OrderState{
    //make it easier since got to write the same thing over and over
    //literally just to make it quicker, ignore 
    private void reasonHelper(Order order){
        throw new IllegalTransitionException("Can't complete since order is in this state: "+ order.getStatus());
    }
    @Override
    public void accept(Order order){
        reasonHelper(order);
    }
    @Override
    public void startPreparing(Order order){
        reasonHelper(order);
    }
    @Override
    public void markReady(Order order){
        reasonHelper(order);
    }
    @Override
    public void reject(Order order,String reason){
        throw new IllegalTransitionException(reason);
    }
    @Override 
    public void cancel(Order order){
        reasonHelper(order);
    }
}
