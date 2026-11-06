package com.fortunategoods.application.dlv.reassignment;

/*
    Class used to send response to frontend
*/
public class StalledOrderResponse {
    private final String orderId;
    private final String restaurantName;
    private final String courierName;

    public StalledOrderResponse(String orderId, String restaurantName, String courierName) {
        this.orderId = orderId;
        this.restaurantName = restaurantName;
        this.courierName = courierName;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getRestaurantName() {
        return restaurantName;
    }

    public String getCourierName() {
        return courierName;
    }
}
