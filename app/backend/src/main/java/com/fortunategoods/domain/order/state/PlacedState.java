package com.fortunategoods.domain.order.state;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.order.DomainRuleViolation;

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
    // Added rejection reason null checking + character limit
    public void reject(Order order,String reason){
        if (reason == null || reason.isBlank()){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Rejection reason is required");
        }

        String stripReason = reason.strip();
        if (stripReason.length() > 500){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Rejection reason cannot exceed 500 characters");
        }
        order.setRejectReason(stripReason);
        order.transitionTo(new RejectedState());
    }

    @Override 
    public void cancel(Order order){
        order.transitionTo(new CanceledState());
    }
}
