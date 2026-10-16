package com.fortunategoods.domain.order;
import com.fortunategoods.domain.handlers.BaseOrderHandler;
import com.fortunategoods.application.ord.OrderRequest;

public class OrderVerify {
    //you need to pass the order request AND the handler to start the chain
    public static void verifyOrder(OrderRequest request, BaseOrderHandler handler){
        if(request.getOrderLines() == null){
            throw new IllegalStateException("Order can't be empty");
        }
        for(OrderLine order: request.getOrderLines()){
            if(order.getQuantity()<=0){
                throw new IllegalStateException("Quantity is invalid!");
            }
            //verify that the item is from the right restaurant 
            if(order.getMenuItem().getRestaurantId().equals(request.getRestaurant().getId()) == false){
                throw new IllegalStateException("Item doesnt match the restaurant");
            }
        }
        //in theory if its not true the exception will be thrown from the chain but putting this as precaution
        if(handler.handle(request) != true){
            throw new IllegalStateException("Order failed the chain of responsability");
        }

    }
}
