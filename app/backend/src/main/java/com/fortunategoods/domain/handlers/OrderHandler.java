package com.fortunategoods.domain.handlers;
import com.fortunategoods.domain.order.validation.OrderRequest;
public interface OrderHandler {
    
    public OrderHandler setNext(OrderHandler nextHandler);
    public boolean handle(OrderRequest orderRequest);
}
