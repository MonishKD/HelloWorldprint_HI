package com.fortunategoods.application.ord.orders.pending;

import com.fortunategoods.domain.order.OrderLine;

/*
    This class will be used to send a proper formatted response
    containing OrderLines inside a PendingOrderResponse, to the 
    frontend 
*/
public class PendingOrderLineResponse {
    private final String menuItemId;
    private final String name;
    private final int quantity;

    public PendingOrderLineResponse(OrderLine line) {
        this.menuItemId = line.getMenuItemId();
        this.name = line.getName();
        this.quantity = line.getQuantity();
    }

    public String getMenuItemId() {
        return menuItemId;
    }

    public String getName() {
        return name;
    }

    public int getQuantity() {
        return quantity;
    }
}
