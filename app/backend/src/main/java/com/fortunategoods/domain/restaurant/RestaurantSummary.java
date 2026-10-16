package com.fortunategoods.domain.restaurant;

public record RestaurantSummary(String id, String name, RestaurantStatus status, double latitude, double longitude) {

    // Builds the summary from a Restaurant loaded by the repository (the menu is left out).
    public static RestaurantSummary of(Restaurant restaurant) {
        return new RestaurantSummary(restaurant.getId(), restaurant.getName(), restaurant.getStatus(),
                restaurant.getLatitude(), restaurant.getLongitude());
    }
}
