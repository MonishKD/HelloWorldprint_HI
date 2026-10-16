package com.fortunategoods.domain.restaurant;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.fortunategoods.technical.persistence.RestaurantRepository;

class RestaurantServiceTest {

    // Fake repository: no database, findAll returns whatever is in this list.
    private final List<Restaurant> stored = new ArrayList<>();

    private final RestaurantRepository restaurants = new RestaurantRepository(null, null) {
        @Override
        public List<Restaurant> findAll() {
            return stored;
        }
    };

    private final RestaurantService restaurantService = new RestaurantService(restaurants);

    // Every restaurant comes back with its id, name, status and coordinates.
    @Test
    void returnsEveryRestaurantWithStatusAndCoordinates() {
        stored.add(new Restaurant("1", "Cafe", RestaurantStatus.OPEN, 45.5, -73.6, List.of()));
        stored.add(new Restaurant("2", "Deli", RestaurantStatus.PAUSED, 45.4, -73.5, List.of()));
        stored.add(new Restaurant("3", "Bakery", RestaurantStatus.CLOSED, 45.3, -73.4, List.of()));

        List<RestaurantSummary> list = restaurantService.browseRestaurants();

        assertEquals(3, list.size());
        assertEquals(new RestaurantSummary("1", "Cafe", RestaurantStatus.OPEN, 45.5, -73.6), list.get(0));
        assertEquals(RestaurantStatus.PAUSED, list.get(1).status());
        assertEquals(RestaurantStatus.CLOSED, list.get(2).status());
    }

    // No restaurants gives an empty list, not null.
    @Test
    void noRestaurantsGivesAnEmptyList() {
        assertTrue(restaurantService.browseRestaurants().isEmpty());
    }

}
