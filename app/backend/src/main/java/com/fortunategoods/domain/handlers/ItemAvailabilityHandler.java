package com.fortunategoods.domain.handlers;
import com.fortunategoods.application.ord.OrderRequest;
import com.fortunategoods.domain.order.OrderLine;

public class ItemAvailabilityHandler extends BaseOrderHandler {
    @Override 
    public boolean handle(OrderRequest orderRequest){
        for (OrderLine order: orderRequest.getOrderLines()){
            if(order.getMenuItem().getStatus() == com.fortunategoods.domain.restaurant.MenuItemStatus.SOLD_OUT){
                throw new IllegalStateException("Item "+order.getMenuItem().getName()+ " is sold out");
            }

        }
        
            //probably will end here
            if (nextHandler != null) {
                return nextHandler.handle(orderRequest);
            }
            return true;
        
    }
    
}
