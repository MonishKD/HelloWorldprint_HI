package com.fortunategoods.domain.handlers;
import com.fortunategoods.application.ord.OrderRequest;
public abstract class BaseOrderHandler implements OrderHandler {
    protected OrderHandler nextHandler;

    @Override
    public OrderHandler setNext(OrderHandler nextHandler) {
        this.nextHandler = nextHandler;
        return nextHandler;
    }

    //concrete will deal with implementation
    public abstract boolean handle(OrderRequest orderRequest);
    
}
