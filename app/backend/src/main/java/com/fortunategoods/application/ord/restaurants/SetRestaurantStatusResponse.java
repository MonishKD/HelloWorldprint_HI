package com.fortunategoods.application.ord.restaurants;

import java.util.Locale;

import com.fortunategoods.domain.restaurant.RestaurantStatus;

/*
    Class that will be used to send back to frontend
*/
public class SetRestaurantStatusResponse {
    private final String restaurantId;
    private final String status;

    public SetRestaurantStatusResponse(
            String restaurantId, RestaurantStatus status) {

        this.restaurantId = restaurantId;
        this.status = status.name().toLowerCase(Locale.ROOT);
    }

    public String getRestaurantId() {
        return restaurantId;
    }

    public String getStatus() {
        return status;
    }
}
