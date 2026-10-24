package com.fortunategoods.technical.security;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.technical.security.role.RoleCheck;
import com.fortunategoods.technical.security.session.SpringSecuritySessionValidator;
import static com.fortunategoods.testsupport.TestData.logIn;

// Story #14: an authenticated user's command is accepted, everyone else is refused.
// Uses the real CommandInvoker, SessionValidator and Login. Nothing here talks to a database.
class CommandNeedsSessionTest {

    private final CommandInvoker commandInvoker =
            new CommandInvoker(new SpringSecuritySessionValidator(new RoleCheck()), mock(CommandLogRepository.class));

    // A command any signed-in customer may run. Remembers whether it actually ran.
    private boolean commandRan = false;
    private final Command<String> customerCommand = new Command<>() {
        @Override
        public Set<UserRole> validRoles() {
            return Set.of(UserRole.CUSTOMER);
        }

        @Override
        public CommandSuccess<String> execute() {
            commandRan = true;
            return CommandSuccess.success("done", "ok");
        }
    };

    @AfterEach
    void forgetLoggedInUser() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void signedInUsersCommandIsExecuted() {
        Session session = logIn(UserRole.CUSTOMER);

        assertEquals("done", commandInvoker.process(customerCommand, session).getData());
        assertTrue(commandRan);
    }

    @Test
    void noSessionIsRefusedAndCommandNeverRuns() {
        assertThrows(AuthenticationException.class, () -> commandInvoker.process(customerCommand, null));
        assertFalse(commandRan);
    }

    // Nobody logged in, but the request makes up a Session: still refused, the client can't fake who they are.
    @Test
    void madeUpSessionIsRefused() {
        assertThrows(AuthenticationException.class, () -> commandInvoker.process(customerCommand, new Session(1, UserRole.CUSTOMER)));
        assertFalse(commandRan);
    }
}
