package com.fortunategoods.application.ord.restaurants;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.ord.menu.BrowseMenuCommand;
import com.fortunategoods.application.ord.menu.MenuService;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.restaurant.RestaurantSummary;
import com.fortunategoods.domain.restaurant.menu.RestaurantMenu;
import com.fortunategoods.technical.security.SessionValidator;

/*
    REST Endpoints for browsing restaurants, used by the frontend's
    RestaurantListPage and MenuPage. Both are read-only.
    Changing a menu (add, edit, availability) is in MenuController.
*/
@RestController
@RequestMapping("/api/restaurants")
public class RestaurantController {
    private final CommandInvoker invoker; // used to call concrete command class
    private final RestaurantService restaurantService; // receiver of the restaurant list command
    private final MenuService menuService; // receiver of the browse menu command
    private final SessionValidator sessionValidator; // used to read the session

    public RestaurantController(CommandInvoker invoker, RestaurantService restaurantService, MenuService menuService, SessionValidator sessionValidator) {
        this.invoker = invoker;
        this.restaurantService = restaurantService;
        this.menuService = menuService;
        this.sessionValidator = sessionValidator;
    }

    // API Endpoint for the list of restaurants: id, name, status and coordinates of each.
    // No restaurants: an empty list []. Not signed in: 401 (Spring Security).
    @GetMapping
    public ResponseEntity<CommandSuccess<List<RestaurantSummary>>> browseRestaurants() {
        // Get current session
        Session session = sessionValidator.currentSession();

        // Create a new command
        BrowseRestaurantsCommand command = new BrowseRestaurantsCommand(restaurantService);

        // Process the command: session and role are checked before it runs
        CommandSuccess<List<RestaurantSummary>> result = invoker.process(command, session);

        // Send the list back to frontend
        return ResponseEntity.ok(result);
    }

    // API Endpoint for one restaurant's menu: the restaurant's status and each item's name, price and availability.
    // Customers see any menu, staff only their own. Bad id: 400, unknown restaurant: 404 (OrderExceptionHandler).
    @GetMapping("/{restaurantId}/menu")
    public ResponseEntity<CommandSuccess<RestaurantMenu>> browseMenu(@PathVariable("restaurantId") String restaurantId) {
        // Get current session (who is asking comes from the session, never from the request)
        Session session = sessionValidator.currentSession();

        // Create a new command
        BrowseMenuCommand command = new BrowseMenuCommand(menuService, session, restaurantId);

        // Process the command: session and role are checked before it runs
        CommandSuccess<RestaurantMenu> result = invoker.process(command, session);

        // Send the menu back to frontend
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{restaurantId}/status")
    public ResponseEntity<CommandSuccess<SetRestaurantStatusResponse>> setRestaurantStatus(@PathVariable("restaurantId") String restaurantId, @RequestBody SetRestaurantStatusReqJSON request) {
        // Get current session
        Session session = sessionValidator.currentSession();

        // Create new command
        SetRestaurantStatusCommand command = new SetRestaurantStatusCommand(restaurantService, session, restaurantId, request.getStatus());

        // Process the command and save it's result
        CommandSuccess<SetRestaurantStatusResponse> result = invoker.process(command, session);

        // Send result back to frontend
        return ResponseEntity.ok(result);
    }
}
