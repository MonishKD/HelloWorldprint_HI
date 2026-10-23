package com.fortunategoods.domain.handlers;
import com.fortunategoods.domain.order.validation.OrderRequest;
public interface OrderHandler {
    
    public void setNextHandler(OrderHandler nextHandler);
    public boolean handle(OrderRequest orderRequest);
}
