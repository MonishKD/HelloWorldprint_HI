package com.fortunategoods.application.ord.orders;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.ord.orders.pending.GetPendingOrdersCommand;
import com.fortunategoods.application.ord.orders.pending.PendingOrderResponse;
import com.fortunategoods.application.ord.orders.preparation.GetPreparationQueueCommand;
import com.fortunategoods.application.ord.orders.preparation.GetPreparationQueueResponse;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.technical.security.SessionValidator;

@RestController
@RequestMapping("/api/restaurants")
public class RestaurantOrderController {
    private final CommandInvoker invoker;
    private final RestaurantOrderService service;
    private final SessionValidator sessionValidator;

    public RestaurantOrderController(CommandInvoker invoker, RestaurantOrderService service, SessionValidator sessionValidator){
        this.invoker = invoker;
        this.service = service;
        this.sessionValidator = sessionValidator;
    }

    // Get pending orders for restaurant 
    @GetMapping("/{restaurantId}/orders")
    public CommandSuccess<List<PendingOrderResponse>> getPendingOrders(@PathVariable("restaurantId") String restaurantId, @RequestParam(name="status", defaultValue="placed") String status){
        // Get the user's session
        Session session = sessionValidator.currentSession();
        // Create the command
        GetPendingOrdersCommand cmd = new GetPendingOrdersCommand(service, session, restaurantId, status); 
        // Run the command
        return invoker.process(cmd, session);
    }

    // Get orders in PQ
    @GetMapping("/{restaurantId}/queue")
    public CommandSuccess<List<GetPreparationQueueResponse>> getPreparationQueue(@PathVariable("restaurantId") String restaurantId) {
        // Get the user's session
        Session session = sessionValidator.currentSession();
        // Create the command
        GetPreparationQueueCommand command = new GetPreparationQueueCommand(service, session, restaurantId);
        // Run the command
        return invoker.process(command, session);
    }
}
