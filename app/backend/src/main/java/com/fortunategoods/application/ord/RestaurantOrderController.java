package com.fortunategoods.application.ord;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
        Session session = sessionValidator.currentSession();

        GetPendingOrdersCommand cmd = new GetPendingOrdersCommand(service, session, restaurantId, status); 
        return invoker.process(cmd, session);
    }
}
