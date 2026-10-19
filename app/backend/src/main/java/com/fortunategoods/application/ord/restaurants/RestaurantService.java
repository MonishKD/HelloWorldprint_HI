package com.fortunategoods.application.ord.restaurants;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.order.validation.OrderVerify;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.domain.restaurant.RestaurantSummary;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;

import jakarta.transaction.Transactional;

@Service
public class RestaurantService {

    private final RestaurantRepository restaurantRepository; // reads every restaurant
    private final UserRepository userRepository; // reads every users


    // Spring gives us the repository.
    public RestaurantService(RestaurantRepository restaurantRepository, UserRepository userRepository) {
        this.restaurantRepository = restaurantRepository;
        this.userRepository = userRepository;

    }

    // Returns every restaurant as a summary. Empty list (never null) when there are none.
    public List<RestaurantSummary> browseRestaurants() {
        return restaurantRepository.findAll().stream().map(RestaurantSummary::of).toList();
    }

    // Runs the status operation within a database transaction.
    @Transactional
    public Restaurant setRestaurantStatus(String staffId, UserRole role, String restaurantId, RestaurantStatus status) {
        if (role == null || !OrdModulePermissions.setRestaurantStatusRoles().contains(role)) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN,"Only Restaurant Staffs are allowed to change Restaurant Status");
        }
        // Verify the validity of the IDs
        OrderVerify.requireId(staffId, "Staff ID");
        OrderVerify.requireId(restaurantId, "Restaurant ID");

        // Find the restaurantID using the staff's user id
        String ownRestaurantId = userRepository.findRestaurantIdByUserId(staffId);
        // Compare user's restaurantId to the restaurantId status we are trying to change
        if (!restaurantId.equals(ownRestaurantId)) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN,"You can only change your own restaurant's status");
        }
        // Get restaurant
        Restaurant restaurant = restaurantRepository.findById(restaurantId);

        if (restaurant == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND,"Restaurant not found");
        }

        // Validate before saving.
        restaurant.changeStatus(ownRestaurantId, status);
        restaurantRepository.updateStatus(restaurantId, restaurant.getStatus());

        return restaurant;
    }
}
