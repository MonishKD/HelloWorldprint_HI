package com.fortunategoods.technical.security.session;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.security.role.RoleCheck;
import static com.fortunategoods.testsupport.TestData.logIn;

// The role check (#29): a user logs in with the real Login, then the real validator decides
// whether their role may run a command. Nothing here talks to a database.
class SpringSecuritySessionValidatorTest {

    // Same message CommandInvoker writes in the CommandLog, so the console and the 403 match.
    private static final String NOT_ALLOWED = "You are not allowed to perform this action";

    private final SpringSecuritySessionValidator sessionValidator = new SpringSecuritySessionValidator(new RoleCheck());

    @AfterEach
    void forgetLoggedInUser() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void customerMayRunACommandThatAllowsCustomers() {
        Session customer = logIn(UserRole.CUSTOMER);

        // Passes when nothing is thrown.
        sessionValidator.requireRoles(customer, Set.of(UserRole.CUSTOMER, UserRole.RESTAURANT));
    }

    @Test
    void courierIsRefusedACustomerOnlyCommand() {
        Session courier = logIn(UserRole.COURIER);

        AccessDeniedException refused = assertThrows(AccessDeniedException.class,
                () -> sessionValidator.requireRoles(courier, Set.of(UserRole.CUSTOMER)));
        assertEquals(NOT_ALLOWED, refused.getMessage());
    }

    @Test
    void commandThatAllowsNoRoleIsRefused() {
        Session customer = logIn(UserRole.CUSTOMER);

        AccessDeniedException refused = assertThrows(AccessDeniedException.class,
                () -> sessionValidator.requireRoles(customer, Set.of()));
        assertEquals(NOT_ALLOWED, refused.getMessage());
    }
}
