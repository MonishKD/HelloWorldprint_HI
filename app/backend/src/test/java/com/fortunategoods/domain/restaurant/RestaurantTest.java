package com.fortunategoods.domain.restaurant;

import com.fortunategoods.domain.restaurant.menu.MenuItem;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class RestaurantTest {

    private final Restaurant cafe = new Restaurant("r1", "Cafe", RestaurantStatus.OPEN, 45.5, -73.6, List.of());

    // The restaurant keeps its id, name, status and coordinates (#75).
    @Test
    void keepsDetailsAndCoordinates() {
        assertEquals("r1", cafe.getId());
        assertEquals("Cafe", cafe.getName());
        assertEquals(RestaurantStatus.OPEN, cafe.getStatus());
        assertEquals(45.5, cafe.getLatitude());
        assertEquals(-73.6, cafe.getLongitude());
    }

    // The three statuses are exactly open, paused and closed (#75).
    @Test
    void hasExactlyThreeStatuses() {
        assertEquals(List.of(RestaurantStatus.OPEN, RestaurantStatus.PAUSED, RestaurantStatus.CLOSED),
                List.of(RestaurantStatus.values()));
    }

    // A new menu with this restaurant's items is accepted.
    @Test
    void setMenuAcceptsOwnItems() {
        cafe.setMenu(List.of(new MenuItem("m1", "r1", "Wrap", MenuItemStatus.AVAILABLE, 4.50)));

        assertEquals(1, cafe.getMenu().size());
    }

    // A new menu cannot contain another restaurant's items.
    @Test
    void setMenuRejectsItemsFromAnotherRestaurant() {
        List<MenuItem> otherMenu = List.of(new MenuItem("m2", "r2", "Soup", MenuItemStatus.AVAILABLE, 3.00));

        assertThrows(IllegalArgumentException.class, () -> cafe.setMenu(otherMenu));
    }

}
