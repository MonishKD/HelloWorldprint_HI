package com.fortunategoods.application.prc;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.prc.history.GetHistoryCommand;
import com.fortunategoods.application.prc.history.HistoryResponse;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.technical.security.SessionValidator;

/*
    REST endpoint for the role-scoped history (#264):
      GET /api/history  -> the signed-in user's own history rows, newest first
    @RestController: Spring sends every request that matches the path below to this class,
    and turns what the method returns into JSON. It takes no parameters on purpose: the scope comes from the session.
*/
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final CommandInvoker invoker;
    private final LedgerService ledgerService;
    private final SessionValidator sessionValidator; // gives the session of the signed-in user

    // Spring gives us all of these.
    public HistoryController(CommandInvoker invoker, LedgerService ledgerService, SessionValidator sessionValidator) {
        this.invoker = invoker;
        this.ledgerService = ledgerService;
        this.sessionValidator = sessionValidator;
    }

    @GetMapping
    public ResponseEntity<CommandSuccess<List<HistoryResponse>>> getHistory() {
        Session session = sessionValidator.currentSession();
        GetHistoryCommand command = new GetHistoryCommand(ledgerService, session);
        return ResponseEntity.ok(invoker.process(command, session));
    }
}
