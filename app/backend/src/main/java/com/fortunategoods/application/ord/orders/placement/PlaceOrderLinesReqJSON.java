package com.fortunategoods.application.ord.orders.placement;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

/*
    This class converts all order lines from the REST Endpoint
    into java objects for backend processing through OrderService
*/
public class PlaceOrderLinesReqJSON {
    @JsonProperty("itemId")
    private String menuItemId;
    private BigDecimal quantity;

    public PlaceOrderLinesReqJSON(String menuItemId, BigDecimal quantity){
        this.menuItemId = menuItemId;
        this.quantity = quantity;
    }

    public String getMenuItemId(){
        return menuItemId;
    }

    public BigDecimal getQuantity(){
        return quantity;
    }

    public void setMenuItemId(String menuItemId){
        this.menuItemId = menuItemId;
    }

    public void setQuantity(BigDecimal quantity){
        this.quantity = quantity;
    }
}
