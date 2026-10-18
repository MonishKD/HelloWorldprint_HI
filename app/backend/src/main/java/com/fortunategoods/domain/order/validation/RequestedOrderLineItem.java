package com.fortunategoods.domain.order.validation;

import java.math.BigDecimal;

/*
    This class converts frontend order line requests
    into a backend object to be used to do orderLine 
    validation for COR design pattern
*/
public class RequestedOrderLineItem {
    private final String menuItemId;
    private final BigDecimal quantity;

    public RequestedOrderLineItem(String menuItemId, BigDecimal quantity) {
        this.menuItemId = menuItemId;
        this.quantity = quantity;
    }

    public String getMenuItemId() {
        return menuItemId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }
}
