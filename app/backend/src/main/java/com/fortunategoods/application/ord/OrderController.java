package com.fortunategoods.application.ord;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.technical.security.SessionValidator;

/*
    This class will be used to control the flow of all
    REST Endpoints the frontend sends to the backend.
*/
@RestController 
@RequestMapping("/api/orders")
public class OrderController {
    private final CommandInvoker invoker; // used to call concrete command class
    private final OrderService orderService; // used to handle business logic
    private final SessionValidator sessionValidator; // used to validate session

    public OrderController(CommandInvoker invoker, OrderService orderService, SessionValidator sessionValidator){
        this.invoker = invoker;
        this.orderService = orderService;
        this.sessionValidator = sessionValidator;
    }

    @PostMapping
    public ResponseEntity<CommandSuccess<PlaceOrderResponse>> placeOrder(@RequestBody PlaceOrderJSON req) {
        // Get current session
        Session session = sessionValidator.currentSession();

        // Create a new command
        PlaceOrderCommand command = new PlaceOrderCommand(orderService, session, req.getRestaurantId(), req.toRequestedItems(), req.getInstructions(), req.getDeliveryAddress());

        // Process the command and store success result
        CommandSuccess<PlaceOrderResponse> result = invoker.process(command, session);

        // Create URI to send back to frontend
        URI uri = URI.create("/api/orders/" + result.getData().getOrderId());

        return ResponseEntity.created(uri).body(result);
    }
}
