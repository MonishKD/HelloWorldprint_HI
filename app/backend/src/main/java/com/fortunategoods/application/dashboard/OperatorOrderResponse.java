package com.fortunategoods.application.dashboard;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/*
    One order as the operator sees it (#274): the usual order JSON (DashboardOrder: status, restaurant,
    lines, ...) plus the courier currently assigned to it.
    courierId is null when no courier is offered or carrying the order right now.
*/
public class OperatorOrderResponse {
    private final DashboardOrder order;
    private final String courierId;

    public OperatorOrderResponse(DashboardOrder order, String courierId) {
        this.order = order;
        this.courierId = courierId;
    }

    // @JsonUnwrapped: Jackson writes the order's fields straight into this JSON object
    // ({ "orderId": ..., "status": ..., "courierId": ... }) instead of nesting them under "order".
    @JsonUnwrapped
    public DashboardOrder getOrder() {
        return order;
    }

    public String getCourierId() {
        return courierId;
    }
}
