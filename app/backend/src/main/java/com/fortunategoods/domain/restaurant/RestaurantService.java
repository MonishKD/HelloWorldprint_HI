package com.fortunategoods.domain.restaurant;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fortunategoods.technical.persistence.RestaurantRepository;

@Service
public class RestaurantService {

    private final RestaurantRepository restaurantRepository; // reads every restaurant

    // Spring gives us the repository.
    public RestaurantService(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    // Returns every restaurant as a summary. Empty list (never null) when there are none.
    public List<RestaurantSummary> browseRestaurants() {
        return restaurantRepository.findAll().stream()
                .map(RestaurantSummary::of)
                .toList();
    }
}
