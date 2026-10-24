package com.fortunategoods.domain.handlers;
import com.fortunategoods.domain.order.validation.OrderRequest;

public abstract class BaseOrderHandler implements OrderHandler {
    protected OrderHandler nextHandler;

    @Override
    public void setNextHandler(OrderHandler nextHandler) {
        this.nextHandler = nextHandler;
    }

    //concrete will deal with implementation
    public abstract boolean handle(OrderRequest orderRequest);
    
}
