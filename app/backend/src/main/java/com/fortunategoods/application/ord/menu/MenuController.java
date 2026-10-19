package com.fortunategoods.application.ord.menu;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.technical.security.SessionValidator;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
    REST endpoint for a restaurant to mark one of its menu items available or sold out (#89).
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
}
