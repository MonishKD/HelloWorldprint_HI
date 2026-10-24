package com.fortunategoods.domain.restaurant;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.fortunategoods.domain.restaurant.menu.MenuItem;

class RestaurantTest {

    private final MenuItem otherRestaurantsItem = new MenuItem("m2", "r2", "Soup", MenuItemStatus.AVAILABLE, 3.00);

    // The menu is a copy that can't be changed from outside, and only holds this restaurant's items.
    @Test
    void menuIsAReadOnlyCopy() {
        List<MenuItem> items = new ArrayList<>(List.of(new MenuItem("m1", "r1", "Wrap", MenuItemStatus.AVAILABLE, 4.50)));
        Restaurant restaurant = new Restaurant("r1", "Cafe", RestaurantStatus.OPEN, 45.5, -73.6, items);
        items.add(new MenuItem("m3", "r1", "Salad", MenuItemStatus.AVAILABLE, 3.00));

        assertEquals(1, restaurant.getMenu().size());
        assertThrows(UnsupportedOperationException.class, () -> restaurant.getMenu().clear());
    }

    @Test
    void anotherRestaurantsItemsAreRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new Restaurant("r1", "Cafe", RestaurantStatus.OPEN, 45.5, -73.6, List.of(otherRestaurantsItem)));

        Restaurant cafe = new Restaurant("r1", "Cafe", RestaurantStatus.OPEN, 45.5, -73.6, List.of());
        cafe.setMenu(List.of(new MenuItem("m1", "r1", "Wrap", MenuItemStatus.AVAILABLE, 4.50)));
        assertEquals(1, cafe.getMenu().size());
        assertThrows(IllegalArgumentException.class, () -> cafe.setMenu(List.of(otherRestaurantsItem)));
    }
}
