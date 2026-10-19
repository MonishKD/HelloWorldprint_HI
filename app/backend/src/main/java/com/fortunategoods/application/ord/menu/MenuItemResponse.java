package com.fortunategoods.application.ord.menu;

import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.restaurant.menu.MenuItem;

/*
    The menu item sent back to the frontend after it is added, edited or removed.
    Same field names the frontend's adapters.ts reads: id, restaurantId, name, description, price, originalPrice, status.
    description is here (and not in MenuItem) because MenuItem has no description yet.
*/
public record MenuItemResponse(String id, String restaurantId, String name, String description,
        double price, Double originalPrice, MenuItemStatus status) {

    public static MenuItemResponse of(MenuItem item, String description) {
        return new MenuItemResponse(item.getId(), item.getRestaurantId(), item.getName(), description,
                item.getPrice(), item.getOriginalPrice(), item.getStatus());
    }
}
