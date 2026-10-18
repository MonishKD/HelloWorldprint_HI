package com.fortunategoods.domain.handlers;
import com.fortunategoods.domain.order.validation.OrderRequest;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrderLine;

public class ItemAvailabilityHandler extends BaseOrderHandler {
    @Override 
    public boolean handle(OrderRequest orderRequest){
        for (OrderLine order: orderRequest.getOrderLines()){
            if(order.getMenuItem().getStatus() == com.fortunategoods.domain.restaurant.MenuItemStatus.SOLD_OUT){
                //changed to get name from orderLine instead just to make it more clean
                throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Item " + order.getName() + " is sold out");
            }
            if(order.getQuantity()<=0){
                throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Item " + order.getName() + " quantity is invalid");
            }

        }
        
            //probably will end here
            if (nextHandler != null) {
                return nextHandler.handle(orderRequest);
            }
            return true;
        
    }
    public void setNextHandler(BaseOrderHandler nextHandler){
        this.nextHandler = nextHandler;
    }
    
}
