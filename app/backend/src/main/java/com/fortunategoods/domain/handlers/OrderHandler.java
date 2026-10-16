package com.fortunategoods.domain.handlers;
import com.fortunategoods.application.ord.OrderRequest;
public interface OrderHandler {
    
    public OrderHandler setNext(OrderHandler nextHandler);
    public boolean handler(OrderRequest orderRequest);
}
