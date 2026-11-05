package com.fortunategoods.application.dashboard;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.technical.configuration.SeedLoader;
import com.fortunategoods.technical.security.SessionValidator;

// The operator's Reset route (#276). @RestController makes Spring send what the method returns back as JSON.
@RestController
@RequestMapping("/api/operator")
public class OperatorResetController {
    private final CommandInvoker invoker;
    private final SeedLoader seedLoader;
    private final SessionValidator sessionValidator;

    // Spring gives us these at startup.
    public OperatorResetController(CommandInvoker invoker, SeedLoader seedLoader, SessionValidator sessionValidator) {
        this.invoker = invoker;
        this.seedLoader = seedLoader;
        this.sessionValidator = sessionValidator;
    }

    // POST /api/operator/reset, no body. Only the OPERATOR role may run it (ResetCommand.validRoles).
    @PostMapping("/reset")
    public CommandSuccess<Void> reset() {
        // Get the user's session
        Session session = sessionValidator.currentSession();
        // Create the command
        ResetCommand command = new ResetCommand(seedLoader);
        // Run the command
        return invoker.process(command, session);
    }
}
