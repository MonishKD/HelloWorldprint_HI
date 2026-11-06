package com.fortunategoods.application.prc.summary;

/*
    One item line of an order summary (#262): { name, quantity, priceCents }.
    priceCents is the price of one item, in cents, as it was copied onto the order when it was placed.
*/
public class OrderSummaryLineResponse {
    private final String name;
    private final int quantity;
    private final long priceCents;

    public OrderSummaryLineResponse(String name, int quantity, long priceCents) {
        this.name = name;
        this.quantity = quantity;
        this.priceCents = priceCents;
    }

    public String getName() {
        return name;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getPriceCents() {
        return priceCents;
    }
}
