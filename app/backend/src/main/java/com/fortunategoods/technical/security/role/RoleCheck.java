package com.fortunategoods.technical.security.role;

import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.function.SingletonSupplier;

import com.fortunategoods.domain.user.UserRole;

// The role check (#29): "is the logged-in user's role one of the roles this command allows?"
// SpringSecuritySessionValidator calls it before every command, for CommandInvoker (#68).
//
// Where the two sides come from:
//   The user's role: Login (#28) saved it in the Spring Security session as an "authority", e.g. ROLE_CUSTOMER.
//   The allowed roles: the Domain decides them (#69), and each command returns them with validRoles().
// This class only compares the two, with Spring Security's own role check (no business rules here).
//
// @Component makes Spring create one of these at startup and give it to SpringSecuritySessionValidator.
@Component
public class RoleCheck {

    // Same message CommandInvoker writes in the CommandLog for a 403, so the console and the response match.
    private static final String NOT_ALLOWED_MESSAGE = "You are not allowed to perform this action";

    // Does nothing when the role is allowed. Throws (403) when it isn't.
    public void requireOneOf(Authentication loggedInUser, Set<UserRole> allowedRoles) {
        // Spring Security's role check: "does this user have one of these roles?"
        // It adds the "ROLE_" in front of each name itself, to match what Login saved.
        AuthorityAuthorizationManager<Set<UserRole>> springRoleCheck =
                AuthorityAuthorizationManager.hasAnyRole(roleNames(allowedRoles));

        // Spring asks for the user through a Supplier (an object whose get() returns the user).
        // SingletonSupplier.of() is Spring's ready-made one, holding the user we already have.
        // The second value is what is being protected: here, the command's allowed roles.
        AuthorizationResult decision = springRoleCheck.authorize(SingletonSupplier.of(loggedInUser), allowedRoles);

        if (!decision.isGranted()) {
            throw new AccessDeniedException(NOT_ALLOWED_MESSAGE);
        }
    }

    // Spring's role check wants the roles as text: {CUSTOMER, RESTAURANT} becomes ["CUSTOMER", "RESTAURANT"].
    private String[] roleNames(Set<UserRole> roles) {
        String[] names = new String[roles.size()];
        int position = 0;
        for (UserRole role : roles) {
            names[position] = role.name();
            position++;
        }
        return names;
    }
}
