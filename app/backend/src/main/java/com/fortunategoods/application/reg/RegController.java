package com.fortunategoods.application.reg;

import org.springframework.web.bind.annotation.RestController;

import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.user.SignUpRules;
import com.fortunategoods.domain.user.User;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.security.AuthenticationService;

@RestController
public class RegController {
    private final AuthenticationService authenticationService; // hashes, saves, logs in and out

    public RegController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }
    // Registers a new account, checking the input and the Domain rules. Returns the new account's id, username, email and role.
    public RegistrationResult register(String username, String email, String password, String role,
            String restaurantName, String address) {
        // 1. Required fields
        requireFilled(username, "Username");
        requireFilled(email, "Email");
        requireFilled(password, "Password");

        // 2. Domain rule: one public role, never operator
        UserRole chosenRole = SignUpRules.publicRole(role);

        // 3. Restaurant name and address only for the restaurant role
        boolean hasRestaurantFields = filled(restaurantName) || filled(address);
        if (chosenRole == UserRole.RESTAURANT) {
            requireFilled(restaurantName, "Restaurant name");
            requireFilled(address, "Address");
        } else if (hasRestaurantFields) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID,
                    "Restaurant name and address are only for restaurant accounts");
        }

        // 4. Save the account through AuthenticationService, which hashes the password
        String cleanUsername = username.trim();
        String cleanEmail = email.trim();
        User account = switch (chosenRole) {
            case CUSTOMER -> authenticationService.registerCustomer(cleanUsername, cleanEmail, password);
            case COURIER -> authenticationService.registerCourier(cleanUsername, cleanEmail, password);
            case RESTAURANT -> {
                // The account, its new closed restaurant and the owner link are saved together (#28)
                Restaurant newRestaurant = SignUpRules.newRestaurant(restaurantName);
                yield authenticationService.registerRestaurant(cleanUsername, cleanEmail, password, newRestaurant, address.trim());
            }
            case OPERATOR -> throw new IllegalStateException("SignUpRules already refuses operators");
        };

        return RegistrationResult.of(account);
    }

    // Throws INVALID with "<field> is required" when the value is missing or only spaces.
    private static void requireFilled(String value, String field) {
        if (!filled(value)) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, field + " is required");
        }
    }

    // true when the value has something other than spaces.
    private static boolean filled(String value) {
        return value != null && !value.isBlank();
    }
}
