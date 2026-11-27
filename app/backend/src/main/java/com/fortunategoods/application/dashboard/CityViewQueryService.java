package com.fortunategoods.application.dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.rating.RestaurantRating;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CourierRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RatingRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;

@Service
public class CityViewQueryService {

    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final CourierRepository courierRepository;
    private final RatingRepository ratingRepository;

    public CityViewQueryService(OrderRepository orderRepository, RestaurantRepository restaurantRepository,
            CourierRepository courierRepository, RatingRepository ratingRepository) {
        this.ratingRepository = ratingRepository;
        this.orderRepository = orderRepository;
        this.restaurantRepository = restaurantRepository;
        this.courierRepository = courierRepository;
    }

    // Throws FORBIDDEN for a missing role. An empty city gives empty lists.
    public CityView cityView(UserRole role) {
        OrdModulePermissions.requireRole(role, OrdModulePermissions.viewCityRoles(), "Sign in to see the city");

        Map<String, Map<OrderStatus, Integer>> counts = orderRepository.orderCount();
        Map<String, List<String>> soldOut = restaurantRepository.findSoldOutItemNames();
        Map<String, RestaurantRating> ratings = ratingRepository.findRestaurantRatings();
        List<CityView.CityRestaurant> restaurants = new ArrayList<>();
        for (Restaurant restaurant : restaurantRepository.findAll()) {
            Map<OrderStatus, Integer> count = counts.getOrDefault(restaurant.getId(), Map.of());
            RestaurantRating rating = ratings.get(restaurant.getId());
            restaurants.add(new CityView.CityRestaurant(restaurant.getId(), restaurant.getName(), restaurant.getStatus(),
                restaurant.getLatitude(), restaurant.getLongitude(), count.getOrDefault(OrderStatus.ACCEPTED, 0),
                count.getOrDefault(OrderStatus.PREPARING, 0), count.getOrDefault(OrderStatus.READY, 0),
                soldOut.getOrDefault(restaurant.getId(), List.of()),
                rating == null ? null : rating.average(), rating == null ? 0 : rating.count()));
        }
        return new CityView(restaurants, courierRepository.findOnDuty());
    }
}