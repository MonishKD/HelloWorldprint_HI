package com.fortunategoods.technical.security.session;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.user.User;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.UserRepository;
import com.fortunategoods.technical.security.role.RoleCheck;

// The role check (#29): a user logs in with the real Login, then the real validator decides
// whether their role may run a command. Nothing here talks to a database.
class SpringSecuritySessionValidatorTest {

    private static final String PASSWORD = "secret";

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SpringSecuritySessionValidator sessionValidator = new SpringSecuritySessionValidator(new RoleCheck());

    // Spring Security keeps the logged-in user per request. Clear it so one test can't leak into the next.
    @AfterEach
    void forgetLoggedInUser() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void customerMayRunACommandThatAllowsCustomers() {
        Session customer = logInAs(UserRole.CUSTOMER);

        // Passes when nothing is thrown.
        sessionValidator.requireRoles(customer, Set.of(UserRole.CUSTOMER, UserRole.RESTAURANT));
    }

    @Test
    void courierIsRefusedACustomerOnlyCommand() {
        Session courier = logInAs(UserRole.COURIER);

        try {
            sessionValidator.requireRoles(courier, Set.of(UserRole.CUSTOMER));
            fail("A courier must not run a customer-only command");
        } catch (AccessDeniedException refused) {
            // Same message CommandInvoker writes in the CommandLog, so the console and the 403 match.
            assertEquals("You are not allowed to perform this action", refused.getMessage());
        }
    }

    // Logs in a user with this role through the real Login, and returns their Session.
    private Session logInAs(UserRole role) {
        User account = new User("1", "tester", "tester@mail.com", null, passwordEncoder.encode(PASSWORD), role);

        // Fake accounts table: always finds the one account above.
        UserRepository accounts = new UserRepository(null) {
            @Override
            public User findByUsername(String username) {
                return account;
            }
        };

        // Login saves the user in the request's session, so it needs a request. These are fake ones from Spring.
        ServletRequestAttributes fakeRequest =
                new ServletRequestAttributes(new MockHttpServletRequest(), new MockHttpServletResponse());
        RequestContextHolder.setRequestAttributes(fakeRequest);

        new Login(accounts, passwordEncoder).login("tester", PASSWORD);
        return sessionValidator.currentSession();
    }
}
