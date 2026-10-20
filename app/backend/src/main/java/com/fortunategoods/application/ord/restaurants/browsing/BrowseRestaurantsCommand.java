package com.fortunategoods.application.ord.restaurants.browsing;

import com.fortunategoods.application.ord.restaurants.RestaurantService;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import java.util.List;
import java.util.Set;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.restaurant.RestaurantSummary;
import com.fortunategoods.domain.user.UserRole;


// Command<List<RestaurantSummary>>: this command's result (its data) is a list of RestaurantSummary.
public final class BrowseRestaurantsCommand implements Command<List<RestaurantSummary>> {

    private final RestaurantService restaurantService; // receiver: does the real work
    private final Session session;

    public BrowseRestaurantsCommand(RestaurantService restaurantService, Session session) {
        this.restaurantService = restaurantService;
        this.session = session;
    }

    // Roles allowed to see the list (CUSTOMER). The invoker checks this before execute().
    @Override
    public Set<UserRole> validRoles() {
        return OrdModulePermissions.browseRestaurantsRoles();
    }

    // Only runs after the invoker's checks pass. Asks RestaurantService for the list and wraps it in a success result.
    @Override
    public CommandSuccess<List<RestaurantSummary>> execute() {
        List<RestaurantSummary> restaurants = restaurantService.browseRestaurants(String.valueOf(session.getUserID()), session.getRole());
        return CommandSuccess.success(restaurants, restaurants.size() + " restaurants loaded");
    }
}
