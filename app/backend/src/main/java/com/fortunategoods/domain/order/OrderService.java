package com.fortunategoods.domain.order;
import com.fortunategoods.application.ord.OrderRequest;
import com.fortunategoods.domain.handlers.BaseOrderHandler;

public class OrderService {
    private BaseOrderHandler orderHandler;

    public OrderService(BaseOrderHandler orderHandler){
        this.orderHandler = orderHandler;
    }
    //until the order repo and the queue are implemented just return the boolean value for testing
    public boolean placeOrder(OrderRequest orderRequest){
        //verify first, THEN return the result
        OrderVerify.verifyOrder(orderRequest,orderHandler);
        return orderHandler.handle(orderRequest);
    }
}

