package com.fortunategoods.application.prc.history;

/*
    One row of a history (#264), sent to the frontend as JSON.
    Every role's row has the order id and the restaurant's name. What else it has depends on the role:
    CourierHistoryResponse has no money fields (couriers never see payment details), LedgerHistoryResponse has the fare.
    Spring turns each row into JSON with the fields of its real class, so a courier's JSON has no money keys at all.
*/
public abstract class HistoryResponse {
    private final String orderId;
    private final String restaurantName;

    protected HistoryResponse(String orderId, String restaurantName) {
        this.orderId = orderId;
        this.restaurantName = restaurantName;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getRestaurantName() {
        return restaurantName;
    }
}
