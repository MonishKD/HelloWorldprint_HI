package com.fortunategoods.application.prc;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.prc.summary.GetOrderSummaryCommand;
import com.fortunategoods.application.prc.summary.OrderSummaryResponse;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.technical.security.SessionValidator;

/*
    REST endpoint for a delivered order's summary (#262):
      GET /api/orders/{orderId}/summary  -> lines, fare parts, total, times, restaurant, courier, address, card
    It shares the /api/orders path with OrderController (ORD). It is its own class because the summary is PRC
    (LedgerService is the receiver), and adding LedgerService to OrderController's constructor would break
    OrderControllerTest, which the Docker build compiles. Spring allows two controllers on one path
    as long as two methods never have the same path and HTTP method.
*/
@RestController
@RequestMapping("/api/orders")
public class OrderSummaryController {

    private final CommandInvoker invoker;
    private final LedgerService ledgerService;
    private final SessionValidator sessionValidator; // gives the session of the signed-in user

    // Spring gives us all of these.
    public OrderSummaryController(CommandInvoker invoker, LedgerService ledgerService, SessionValidator sessionValidator) {
        this.invoker = invoker;
        this.ledgerService = ledgerService;
        this.sessionValidator = sessionValidator;
    }

    // @PathVariable reads {orderId} from the URL.
    @GetMapping("/{orderId}/summary")
    public ResponseEntity<CommandSuccess<OrderSummaryResponse>> getOrderSummary(@PathVariable("orderId") String orderId) {
        Session session = sessionValidator.currentSession();
        GetOrderSummaryCommand command = new GetOrderSummaryCommand(ledgerService, session, orderId);
        return ResponseEntity.ok(invoker.process(command, session));
    }
}
