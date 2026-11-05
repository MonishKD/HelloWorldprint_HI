package com.fortunategoods.application.prc.summary;

import java.math.BigDecimal;

/*
    One item line of an order summary (#262): { name, quantity, price }.
    price is the price of one item, in dollars, as it was copied onto the order when it was placed.
*/
public class OrderSummaryLineResponse {
    private final String name;
    private final int quantity;
    private final BigDecimal price;

    public OrderSummaryLineResponse(String name, int quantity, BigDecimal price) {
        this.name = name;
        this.quantity = quantity;
        this.price = price;
    }

    public String getName() {
        return name;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
