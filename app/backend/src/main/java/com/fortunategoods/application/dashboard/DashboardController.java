package com.fortunategoods.application.dashboard;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.technical.security.SessionValidator;

/*
    REST endpoints for the dashboards: the output console feed, and each role's own dashboard data.
    @RestController: Spring sends every request that matches the paths below to this class,
    and turns what the method returns into JSON.
    Reading the console is not a command, so it does not go through CommandInvoker
    (otherwise every refresh would itself be logged). The dashboard reads do, so their role is checked.
*/
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final OutputConsoleFeed outputConsoleFeed; // builds the console entries
    private final SessionValidator sessionValidator;   // gives the session of the signed-in user
    private final CommandInvoker invoker;
    private final DashboardService dashboardService;

    public DashboardController(OutputConsoleFeed outputConsoleFeed, SessionValidator sessionValidator,
            CommandInvoker invoker, DashboardService dashboardService) {
        this.outputConsoleFeed = outputConsoleFeed;
        this.sessionValidator = sessionValidator;
        this.invoker = invoker;
        this.dashboardService = dashboardService;
    }

    // Customers only (403 otherwise). Which customer comes from the session, so nobody can read another's orders.
    @GetMapping("/customer")
    public ResponseEntity<CommandSuccess<CustomerOrderStatusView>> getCustomerDashboard() {
        Session session = sessionValidator.currentSession();
        GetCustomerDashboardCommand command = new GetCustomerDashboardCommand(dashboardService, session);
        return ResponseEntity.ok(invoker.process(command, session));
    }

    // Restaurant staff only (403 otherwise). Their restaurant comes from their account, not the request.
    @GetMapping("/restaurant")
    public ResponseEntity<CommandSuccess<PreparationQueueView>> getRestaurantDashboard() {
        Session session = sessionValidator.currentSession();
        GetRestaurantDashboardCommand command = new GetRestaurantDashboardCommand(dashboardService, session);
        return ResponseEntity.ok(invoker.process(command, session));
    }

    // GET /api/dashboard/console              -> the newest 100 entries of the signed-in user, oldest first
    // GET /api/dashboard/console?before={id}  -> "load more": the 100 entries just older than that id
    // An empty list [] means nothing (more) to show. Not signed in: 401 (Spring Security).
    // @RequestParam reads ?before= from the URL; required = false means it can be left out.
    @GetMapping("/console")
    public ResponseEntity<List<ConsoleEntry>> getConsole(
            @RequestParam(name = "before", required = false) Long beforeId) {

        // Who is asking comes from the session, never from the request.
        Session session = sessionValidator.currentSession();

        List<ConsoleEntry> entries = outputConsoleFeed.entriesFor(session, beforeId);
        return ResponseEntity.ok(entries);
    }
}
