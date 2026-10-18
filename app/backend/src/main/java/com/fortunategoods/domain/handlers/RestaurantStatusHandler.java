package com.fortunategoods.domain.handlers;
import com.fortunategoods.domain.order.validation.OrderRequest;
import com.fortunategoods.domain.order.DomainRuleViolation;

public class RestaurantStatusHandler extends BaseOrderHandler {

    @Override 
    public boolean handle(OrderRequest orderRequest){
        if (orderRequest.getRestaurant().getStatus() == com.fortunategoods.domain.restaurant.RestaurantStatus.CLOSED) {
           throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Restaurant is closed");
        } 
        else if (orderRequest.getRestaurant().getStatus() == com.fortunategoods.domain.restaurant.RestaurantStatus.PAUSED){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Restaurant is paused, try again later");
        }
        else {
            //sends request to next handler
            if (nextHandler != null) {
                return nextHandler.handle(orderRequest);
            }
            return true;
        } 
    }

    public void setNextHandler(BaseOrderHandler nextHandler){
        this.nextHandler = nextHandler;
    }

}
