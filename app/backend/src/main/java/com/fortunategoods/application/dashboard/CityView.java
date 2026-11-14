package com.fortunategoods.application.dashboard;

import java.util.List;

import com.fortunategoods.domain.courier.OnDutyCourier;
import com.fortunategoods.domain.restaurant.RestaurantStatus;

/*
    The state of the city, shown on every dashboard. Every role can read it,
    so it must never hold customer or payment data.
*/
public record CityView(List<CityRestaurant> restaurants, List<OnDutyCourier> couriers) {

    public CityView {
        restaurants = List.copyOf(restaurants);
        couriers = List.copyOf(couriers);
    }

    // queued is the same number as the preparation queue: accepted orders not being prepared yet.
    public record CityRestaurant(String id, String name, RestaurantStatus status, double latitude, double longitude,
            int queued, int inPreparation, int ready, List<String> soldOutItems) {}
}
