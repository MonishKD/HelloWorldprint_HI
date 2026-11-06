package com.fortunategoods.application.prc;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.ord.orders.OrderStatusResponse;
import com.fortunategoods.application.prc.refund.RefundOrderCommand;
import com.fortunategoods.application.prc.summary.GetOrderSummaryCommand;
import com.fortunategoods.application.prc.summary.OrderSummaryResponse;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.technical.security.SessionValidator;

/*
    PRC's routes on an order. They share the /api/orders path with OrderController (ORD). They are in their own class
    because adding PRC services to OrderController's constructor would break OrderControllerTest, which the Docker build
    compiles. Spring allows two controllers on one path as long as two methods never have the same path and HTTP method.
      GET  /api/orders/{orderId}/summary  -> lines, fare parts, total, times, restaurant, courier, address, card
      POST /api/orders/{orderId}/refund   -> the operator's refund of a delivered order
*/
@RestController
@RequestMapping("/api/orders")
public class PrcOrderController {

    private final CommandInvoker invoker;
    private final LedgerService ledgerService;
    private final PaymentService paymentService;
    private final SessionValidator sessionValidator; // gives the session of the signed-in user

    // Spring gives us all of these.
    public PrcOrderController(CommandInvoker invoker, LedgerService ledgerService, PaymentService paymentService, SessionValidator sessionValidator) {
        this.invoker = invoker;
        this.ledgerService = ledgerService;
        this.paymentService = paymentService;
        this.sessionValidator = sessionValidator;
    }

    // @PathVariable reads {orderId} from the URL.
    @GetMapping("/{orderId}/summary")
    public ResponseEntity<CommandSuccess<OrderSummaryResponse>> getOrderSummary(@PathVariable("orderId") String orderId) {
        Session session = sessionValidator.currentSession();
        GetOrderSummaryCommand command = new GetOrderSummaryCommand(ledgerService, session, orderId);
        return ResponseEntity.ok(invoker.process(command, session));
    }

    @PostMapping("/{orderId}/refund")
    public ResponseEntity<CommandSuccess<OrderStatusResponse>> refundOrder(@PathVariable("orderId") String orderId) {
        Session session = sessionValidator.currentSession();
        return ResponseEntity.ok(invoker.process(new RefundOrderCommand(paymentService, session, orderId), session));
    }
}
