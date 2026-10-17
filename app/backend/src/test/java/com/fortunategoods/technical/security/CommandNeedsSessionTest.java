package com.fortunategoods.technical.security;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.*;

import com.fortunategoods.application.ord.*;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.user.*;
import com.fortunategoods.technical.persistence.*;
import com.fortunategoods.technical.security.role.RoleCheck;
import com.fortunategoods.technical.security.session.*;

import tools.jackson.databind.json.JsonMapper;

class CommandNeedsSessionTest {

    private static final String PASSWORD = "secret";

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SpringSecuritySessionValidator sessionValidator = new SpringSecuritySessionValidator(new RoleCheck());

    // Fake command log: saves nothing.
    private final CommandLogRepository commandLog = new CommandLogRepository(null) {
        @Override
        public void save(CommandLog log) { }
    };

    private final CommandInvoker commandInvoker = new CommandInvoker(sessionValidator, commandLog);

    // Fake command any signed-in customer may run. Remembers whether it actually ran.
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

    // Clear the logged-in user so one test can't leak into the next.
    @AfterEach
    void forgetLoggedInUser() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    // Main scenario: user requests a command, authentication is confirmed, the command is executed.
    @Test
    void signedInUsersCommandIsExecuted() {
        Session session = logInAsCustomer();

        CommandSuccess<String> result = commandInvoker.process(customerCommand, session);

        assertTrue(commandRan);
        assertEquals("done", result.getData());
    }

    // Failure path: no valid session, the request is denied and the command never runs.
    @Test
    void noSessionIsRefusedAndCommandNeverRuns() {
        assertThrows(AuthenticationException.class, () -> commandInvoker.process(customerCommand, null));

        assertFalse(commandRan);
    }

    // Nobody logged in, but the request makes up a Session: still refused, the client can't fake who they are.
    @Test
    void madeUpSessionIsRefused() {
        Session madeUp = new Session(1, UserRole.CUSTOMER);

        assertThrows(AuthenticationException.class, () -> commandInvoker.process(customerCommand, madeUp));

        assertFalse(commandRan);
    }

    // Failure path: the browser gets a 401 with a message, so the frontend sends the user back to login.
    @Test
    void noSessionAnswersA401WithAMessage() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityErrorResponses errorResponses = new SecurityErrorResponses(JsonMapper.builder().build());

        errorResponses.commence(new MockHttpServletRequest(), response, new AuthenticationCredentialsNotFoundException("no session"));

        assertEquals(401, response.getStatus());
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("\"code\":\"UNAUTHENTICATED\""));
        assertTrue(body.contains("\"message\":\"Sign-in again to perform this action\""));
    }

    // Logs in a customer (id 1) through the real Login, and returns their Session.
    private Session logInAsCustomer() {
        User account = new User("1", "tester", "tester@mail.com", null, passwordEncoder.encode(PASSWORD), UserRole.CUSTOMER);

        // Fake accounts table: always finds the one account above.
        UserRepository accounts = new UserRepository(null) {
            @Override
            public User findByUsername(String username) {
                return account;
            }
        };

        // Login saves the user in the request's session, so it needs a (fake) request.
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest(), new MockHttpServletResponse()));

        new Login(accounts, passwordEncoder).login("tester", PASSWORD);
        return sessionValidator.currentSession();
    }
}
