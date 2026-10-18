package com.fortunategoods.application.ord.restaurants;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.restaurant.RestaurantSummary;

@Service
public class RestaurantCommands {

    private final CommandInvoker commandInvoker;         // checks session and role, then runs the command
    private final RestaurantService restaurantService;   // handed to each command as its receiver

    // Spring gives us both of these.
    public RestaurantCommands(CommandInvoker commandInvoker, RestaurantService restaurantService) {
        this.commandInvoker = commandInvoker;
        this.restaurantService = restaurantService;
    }

    // Returns the restaurant list, or throws the invoker's failure (not signed in, wrong role...).
    public CommandSuccess<List<RestaurantSummary>> browseRestaurants(Session session) {
        // 1. build the command, 2. let the invoker check and run it
        BrowseRestaurantsCommand command = new BrowseRestaurantsCommand(restaurantService);
        return commandInvoker.process(command, session);
    }
}
