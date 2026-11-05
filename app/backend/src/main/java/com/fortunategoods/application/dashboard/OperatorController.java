package com.fortunategoods.application.dashboard;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.technical.security.SessionValidator;

/*
    REST endpoints for the operator's reads, under /api/operator (#274).
    Later operator reads (the dispatch queue, stalled orders) go here too.
    @RestController: Spring sends every request that matches the paths below to this class,
    and turns what the method returns into JSON.
*/
@RestController
@RequestMapping("/api/operator")
public class OperatorController {

    private final SessionValidator sessionValidator; // gives the session of the signed-in user
    private final CommandInvoker invoker;
    private final DashboardService dashboardService;

    // Spring gives us all of these.
    public OperatorController(SessionValidator sessionValidator, CommandInvoker invoker,
            DashboardService dashboardService) {
        this.sessionValidator = sessionValidator;
        this.invoker = invoker;
        this.dashboardService = dashboardService;
    }

    // Operator only (403 otherwise). Every order, with its current state, restaurant and courier.
    @GetMapping("/orders")
    public ResponseEntity<CommandSuccess<List<OperatorOrderResponse>>> getAllOrders() {
        Session session = sessionValidator.currentSession();
        GetAllOrdersCommand command = new GetAllOrdersCommand(dashboardService, session);
        return ResponseEntity.ok(invoker.process(command, session));
    }
}
