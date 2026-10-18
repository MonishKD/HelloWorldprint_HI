package com.fortunategoods.domain.restaurant;

import com.fortunategoods.domain.restaurant.menu.MenuItem;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class MenuItemTest {

    @Test
    void availableItemIsNotSoldOut() {
        MenuItem item = new MenuItem("m1", "r1", "Veggie Wrap", MenuItemStatus.AVAILABLE, 4.50);

        assertTrue(item.isAvailable());
        assertFalse(item.isSoldOut());
    }

    @Test
    void soldOutItemIsNotAvailable() {
        MenuItem item = new MenuItem("m1", "r1", "Veggie Wrap", MenuItemStatus.SOLD_OUT, 4.50);

        assertTrue(item.isSoldOut());
        assertFalse(item.isAvailable());
    }

    @Test
    void exposesDisplayDataAndRestaurant() {
        MenuItem item = new MenuItem("m1", "r1", "Veggie Wrap", MenuItemStatus.AVAILABLE, 4.50);

        assertEquals("m1", item.getId());
        assertEquals("r1", item.getRestaurantId());
        assertEquals("Veggie Wrap", item.getName());
        assertEquals(MenuItemStatus.AVAILABLE, item.getStatus());
        assertEquals(4.50, item.getPrice());
    }

    @Test
    void rejectsInvalidData() {
        assertThrows(IllegalArgumentException.class,
                () -> new MenuItem(" ", "r1", "Wrap", MenuItemStatus.AVAILABLE, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new MenuItem("m1", null, "Wrap", MenuItemStatus.AVAILABLE, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new MenuItem("m1", "r1", "", MenuItemStatus.AVAILABLE, 1.0));
        assertThrows(NullPointerException.class,
                () -> new MenuItem("m1", "r1", "Wrap", null, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new MenuItem("m1", "r1", "Wrap", MenuItemStatus.AVAILABLE, -1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new MenuItem("m1", "r1", "Wrap", MenuItemStatus.AVAILABLE, Double.NaN));
    }

    @Test
    void restaurantMenuCannotBeModified() {
        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem("m1", "r1", "Wrap", MenuItemStatus.AVAILABLE, 4.50));
        Restaurant restaurant = new Restaurant("r1", "Cafe", RestaurantStatus.OPEN, 45.5, -73.6, items);

        items.add(new MenuItem("m2", "r1", "Soup", MenuItemStatus.AVAILABLE, 3.00));

        assertEquals(1, restaurant.getMenu().size());
        assertThrows(UnsupportedOperationException.class, () -> restaurant.getMenu().clear());
    }

    @Test
    void restaurantRejectsItemsFromAnotherRestaurant() {
        List<MenuItem> items = List.of(new MenuItem("m1", "r2", "Wrap", MenuItemStatus.AVAILABLE, 4.50));

        assertThrows(IllegalArgumentException.class,
                () -> new Restaurant("r1", "Cafe", RestaurantStatus.OPEN, 45.5, -73.6, items));
    }

}
