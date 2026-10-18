package com.fortunategoods.domain.restaurant.menu;

import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import java.util.List;

/*
 * This class is used to represent a restaurant and its menu items. It is used to transfer data between the backend and the frontend.
 * It is a record, which means that it is immutable and has a fixed set of fields. The fields are:
 * - restaurantId: the id of the restaurant
 * - restaurantName: the name of the restaurant
 * - restaurantStatus: the status of the restaurant (OPEN, CLOSED, etc.)
 * - items: the list of menu items for the restaurant
*/
public record RestaurantMenu(String restaurantId, String restaurantName, RestaurantStatus restaurantStatus,
        List<MenuItem> items) {

    // Keeps a read-only copy of the items, so nobody can change the menu after it is created.
    public RestaurantMenu {
        items = List.copyOf(items);
    }

    // Builds the menu from a Restaurant loaded by the repository.
    public static RestaurantMenu of(Restaurant restaurant) {
        return new RestaurantMenu(restaurant.getId(), restaurant.getName(), restaurant.getStatus(), restaurant.getMenu());
    }
}
