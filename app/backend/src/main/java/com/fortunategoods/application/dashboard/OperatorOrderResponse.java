package com.fortunategoods.application.dashboard;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/*
    One order as the operator sees it (#274): the usual order JSON (DashboardOrder: status, lines, ...) plus
    the restaurant's name and the courier currently assigned to it, by id and by name (the frontend's Order reads all three).
    restaurantName is null when the restaurant no longer exists.
    courierId and courierName are null when no courier is offered or carrying the order right now.
    chargedTotal is what the order cost with fees, in dollars. null until the order has a CHARGE ledger entry.
*/
public class OperatorOrderResponse {
    private final DashboardOrder order;
    private final String restaurantName;
    private final String courierId;
    private final String courierName;
    private final Double chargedTotal;

    public OperatorOrderResponse(DashboardOrder order, String restaurantName, String courierId, String courierName, Double chargedTotal) {
        this.order = order;
        this.restaurantName = restaurantName;
        this.courierId = courierId;
        this.courierName = courierName;
        this.chargedTotal = chargedTotal;
    }

    // @JsonUnwrapped: Jackson writes the order's fields straight into this JSON object
    // ({ "orderId": ..., "status": ..., "courierId": ... }) instead of nesting them under "order".
    @JsonUnwrapped
    public DashboardOrder getOrder() {
        return order;
    }

    public String getRestaurantName() {
        return restaurantName;
    }

    public String getCourierId() {
        return courierId;
    }

    public String getCourierName() {
        return courierName;
    }

    public Double getChargedTotal() {
        return chargedTotal;
    }
}
