package com.fortunategoods.application.prc;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.ord.orders.placement.PlaceOrderReqJSON;
import com.fortunategoods.application.ord.orders.placement.PlaceOrderResponse;
import com.fortunategoods.application.prc.checkout.CheckoutCommand;
import com.fortunategoods.application.prc.checkout.CheckoutResponse;
import com.fortunategoods.application.prc.checkout.PlacePaidOrderCommand;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.technical.security.SessionValidator;

/*
    REST endpoints for paying an order by card (#259). The cart (#260) calls them in this order:
      POST /api/payments/checkout     same body as placing an order -> fare parts, paymentId and clientSecret
      (the browser confirms the card with Stripe using the clientSecret)
      POST /api/payments/place-order  same body as placing an order + "paymentId" -> the placed order
    Both go through CommandInvoker, so they are checked, logged and shown in the console like every other command.
*/
@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final CommandInvoker invoker;
    private final PaymentService paymentService;
    private final SessionValidator sessionValidator;

    public PaymentController(CommandInvoker invoker, PaymentService paymentService, SessionValidator sessionValidator) {
        this.invoker = invoker;
        this.paymentService = paymentService;
        this.sessionValidator = sessionValidator;
    }

    // Prices the cart and holds that amount on the card. Instructions and address are not needed yet.
    @PostMapping("/checkout")
    public ResponseEntity<CommandSuccess<CheckoutResponse>> checkout(@RequestBody PlaceOrderReqJSON req) {
        Session session = sessionValidator.currentSession();

        CheckoutCommand command = new CheckoutCommand(paymentService, req.getRestaurantId(), req.toRequestedItems());

        return ResponseEntity.ok(invoker.process(command, session));
    }

    // Places the order once the browser confirmed the card.
    @PostMapping("/place-order")
    public ResponseEntity<CommandSuccess<PlaceOrderResponse>> placePaidOrder(@RequestBody PlaceOrderReqJSON req) {
        Session session = sessionValidator.currentSession();

        PlacePaidOrderCommand command = new PlacePaidOrderCommand(paymentService, session, req.getRestaurantId(), req.toRequestedItems(),
                req.getInstructions(), req.getDeliveryAddress(), req.getPaymentId());

        CommandSuccess<PlaceOrderResponse> result = invoker.process(command, session);

        URI uri = URI.create("/api/orders/" + result.getData().getOrderId());
        return ResponseEntity.created(uri).body(result);
    }
}
