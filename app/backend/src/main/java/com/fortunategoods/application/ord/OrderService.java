package com.fortunategoods.application.ord;
import com.fortunategoods.domain.handlers.BaseOrderHandler;
import com.fortunategoods.domain.order.OrderVerify;
import com.fortunategoods.domain.order.StateDesignPattern.*;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderStatus;

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

    public void acceptOrder(Order order){
        order.accept();
    }
    public void prepareOrder(Order order){
        order.startPreparing();
    }
    public void cancelOrder(Order order){
        order.cancel();
    }
    public void markAsReadyOrder(Order order){
        order.markReady();
    }



}
