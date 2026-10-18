package com.fortunategoods.application.ord.restaurants;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import java.util.List;
import java.util.Set;

import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.restaurant.RestaurantSummary;
import com.fortunategoods.domain.user.UserRole;


// Command<List<RestaurantSummary>>: this command's result (its data) is a list of RestaurantSummary.
public final class BrowseRestaurantsCommand implements Command<List<RestaurantSummary>> {

    private final RestaurantService restaurantService; // receiver: does the real work

    public BrowseRestaurantsCommand(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    // Roles allowed to see the list (CUSTOMER). The invoker checks this before execute().
    @Override
    public Set<UserRole> validRoles() {
        return OrdModulePermissions.browseRestaurantsRoles();
    }

    // Only runs after the invoker's checks pass. Asks RestaurantService for the list and wraps it in a success result.
    @Override
    public CommandSuccess<List<RestaurantSummary>> execute() {
        List<RestaurantSummary> restaurants = restaurantService.browseRestaurants();
        return CommandSuccess.success(restaurants, restaurants.size() + " restaurants loaded");
    }
}
