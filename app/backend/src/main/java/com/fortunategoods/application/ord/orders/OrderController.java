package com.fortunategoods.application.ord.orders;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.ord.orders.acceptance.AcceptOrderCommand;
import com.fortunategoods.application.ord.orders.acceptance.AcceptOrderResponse;
import com.fortunategoods.application.ord.orders.cancellation.CancelOrderCommand;
import com.fortunategoods.application.ord.orders.cancellation.CancelOrderResponse;
import com.fortunategoods.application.ord.orders.placement.PlaceOrderCommand;
import com.fortunategoods.application.ord.orders.placement.PlaceOrderReqJSON;
import com.fortunategoods.application.ord.orders.placement.PlaceOrderResponse;
import com.fortunategoods.application.ord.orders.preparation.PrepareOrderResponse;
import com.fortunategoods.application.ord.orders.preparation.PreparingOrderCommand;
import com.fortunategoods.application.ord.orders.ready.ReadyOrderCommand;
import com.fortunategoods.application.ord.orders.ready.ReadyOrderResponse;
import com.fortunategoods.application.ord.orders.rejection.RejectOrderCommand;
import com.fortunategoods.application.ord.orders.rejection.RejectOrderReqJSON;
import com.fortunategoods.application.ord.orders.rejection.RejectOrderResponse;
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

    // API Endpoint for placing order
    @PostMapping
    public ResponseEntity<CommandSuccess<PlaceOrderResponse>> placeOrder(@RequestBody PlaceOrderReqJSON req) {
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

    // API Endpoint for cancelling an order
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<CommandSuccess<CancelOrderResponse>> cancelOrder(@PathVariable("orderId") String orderId){
        // Get current session
        Session session = sessionValidator.currentSession();

        // Create a new command
        CancelOrderCommand command = new CancelOrderCommand(orderService, session, orderId);

        // Process the command and store success result
        CommandSuccess<CancelOrderResponse> result = invoker.process(command, session);

        // Confirm the cancellation request to frontend
        return ResponseEntity.ok(result);
    }

    // API Endpoint for acceptinng an order
    @PostMapping("/{orderId}/accept")
    public ResponseEntity<CommandSuccess<AcceptOrderResponse>> acceptOrder(@PathVariable("orderId") String orderId){
        // Get current session
        Session session = sessionValidator.currentSession();

        // Create a new command
        AcceptOrderCommand command = new AcceptOrderCommand(orderService, session, orderId);

        // Process the command and store success result
        CommandSuccess<AcceptOrderResponse> result = invoker.process(command, session);

        // Confirm the accept request to frontend
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{orderId}/reject")
    public ResponseEntity<CommandSuccess<RejectOrderResponse>> rejectOrder(@PathVariable("orderId") String orderId, @RequestBody(required = false) RejectOrderReqJSON request){
        // Get current session
        Session session = sessionValidator.currentSession();

        // No body means no reason: pass null so the Domain answers "Rejection reason is required"
        String reason = request == null ? null : request.getReason();

        // Create a new command
        RejectOrderCommand command = new RejectOrderCommand(orderService, session, orderId, reason);

        // Process the command and store success result
        CommandSuccess<RejectOrderResponse> result = invoker.process(command, session);

        // Confirm the reject request to frontend
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{orderId}/ready")
    public ResponseEntity<CommandSuccess<ReadyOrderResponse>> markOrderReady(@PathVariable("orderId") String orderId) {
        // Get current session
        Session session = sessionValidator.currentSession();

        // Create a new command
        ReadyOrderCommand command = new ReadyOrderCommand(orderService, session, orderId);

        // Process the command and store success result
        CommandSuccess<ReadyOrderResponse> result = invoker.process(command, session);

        // Confirm the ready request to frontend
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{orderId}/prepare")
    public ResponseEntity<CommandSuccess<PrepareOrderResponse>> prepareOrder(@PathVariable("orderId") String orderId) {
        // Get current session
        Session session = sessionValidator.currentSession();

        // Create a new command
        PreparingOrderCommand command = new PreparingOrderCommand(orderService, session, orderId);

        // Process command and store success result
        CommandSuccess<PrepareOrderResponse> result = invoker.process(command, session);

        // Confirm and send the request to frontend
        return ResponseEntity.ok(result);
    }
}
