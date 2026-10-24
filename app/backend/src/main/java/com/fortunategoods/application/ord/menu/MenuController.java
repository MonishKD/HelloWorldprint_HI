package com.fortunategoods.application.ord.menu;

import com.fortunategoods.application.ord.menu.availability.UpdateItemAvailabilityCommand;
import com.fortunategoods.application.ord.menu.availability.UpdateItemAvailabilityReqJSON;
import com.fortunategoods.application.ord.menu.availability.UpdateItemAvailabilityResponse;
import com.fortunategoods.application.ord.menu.browsing.BrowseMenuCommand;
import com.fortunategoods.application.ord.menu.management.AddMenuItemCommand;
import com.fortunategoods.application.ord.menu.management.EditMenuItemCommand;
import com.fortunategoods.application.ord.menu.management.MenuItemReqJSON;
import com.fortunategoods.application.ord.menu.management.MenuItemResponse;
import com.fortunategoods.application.ord.menu.management.RemoveMenuItemCommand;
import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.restaurant.menu.RestaurantMenu;
import com.fortunategoods.technical.security.SessionValidator;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
    REST endpoints for a restaurant to change its own menu:
      mark an item available or sold out, and add, edit or remove items.
    @RestController: Spring sends every request that matches the paths below to this class,
    and turns what the method returns into JSON.
    Errors (400 invalid, 403 forbidden, 404 not found) are turned into responses by OrderExceptionHandler.
*/
@RestController
@RequestMapping("/api/restaurants")
public class MenuController {

    private final CommandInvoker invoker;           // checks session and role, then runs the command
    private final MenuService menuService;          // handed to the command as its receiver
    private final SessionValidator sessionValidator; // gives the session of the signed-in user

    // Spring gives us all three of these.
    public MenuController(CommandInvoker invoker, MenuService menuService, SessionValidator sessionValidator) {
        this.invoker = invoker;
        this.menuService = menuService;
        this.sessionValidator = sessionValidator;
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

    // PATCH /api/restaurants/{restaurantId}/menu/{itemId}/availability with body { "status": "sold_out" }
    // Returns 200 with the updated item. Who is asking comes from the session, never from the body.
    @PatchMapping("/{restaurantId}/menu/{itemId}/availability")
    public ResponseEntity<CommandSuccess<UpdateItemAvailabilityResponse>> updateItemAvailability(

            @PathVariable("restaurantId") String restaurantId,
            @PathVariable("itemId") String itemId,
            @RequestBody UpdateItemAvailabilityReqJSON req) {

            Session session = sessionValidator.currentSession();

        // 1. build the command,
        // 2. let the invoker check and run it
        UpdateItemAvailabilityCommand command = new UpdateItemAvailabilityCommand(menuService, session, restaurantId, itemId, req.toStatus());
        CommandSuccess<UpdateItemAvailabilityResponse> result = invoker.process(command, session);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/{restaurantId}/menu")
    public ResponseEntity<CommandSuccess<MenuItemResponse>> addMenuItem(
            @PathVariable("restaurantId") String restaurantId,
            @RequestBody MenuItemReqJSON req) {

        Session session = sessionValidator.currentSession();

        AddMenuItemCommand command = new AddMenuItemCommand(menuService, session, restaurantId, req.toInput());
        CommandSuccess<MenuItemResponse> result = invoker.process(command, session);

        URI uri = URI.create("/api/restaurants/" + restaurantId + "/menu/" + result.getData().id());
        return ResponseEntity.created(uri).body(result);
    }

    @PatchMapping("/{restaurantId}/menu/{itemId}")
    public ResponseEntity<CommandSuccess<MenuItemResponse>> editMenuItem(
            @PathVariable("restaurantId") String restaurantId,
            @PathVariable("itemId") String itemId,
            @RequestBody MenuItemReqJSON req) {

        Session session = sessionValidator.currentSession();

        EditMenuItemCommand command = new EditMenuItemCommand(menuService, session, restaurantId, itemId, req.toInput());
        CommandSuccess<MenuItemResponse> result = invoker.process(command, session);

        return ResponseEntity.ok(result);
    }

    // Hidden, not deleted, so past orders stay intact.
    @DeleteMapping("/{restaurantId}/menu/{itemId}")
    public ResponseEntity<CommandSuccess<MenuItemResponse>> removeMenuItem(
            @PathVariable("restaurantId") String restaurantId,
            @PathVariable("itemId") String itemId) {

        Session session = sessionValidator.currentSession();

        RemoveMenuItemCommand command = new RemoveMenuItemCommand(menuService, session, restaurantId, itemId);
        CommandSuccess<MenuItemResponse> result = invoker.process(command, session);

        return ResponseEntity.ok(result);
    }
}
