//written by Monish. had a merge conflict issue with two RegControllers made by different people.
package com.fortunategoods.domain.user;

import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;

import java.util.List;

public final class SignUpRules {
    private SignUpRules() {} // No instances will be created

    // Turns the role typed in the form ("customer", "COURIER"...) into a UserRole, or refuses it.
    public static UserRole publicRole(String role) {
        if (role == null || role.isBlank()) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Choose a role: customer, restaurant or courier");
        }

        UserRole parsed;
        try {
            parsed = UserRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException unknown) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Unknown role: " + role);
        }

        if (parsed == UserRole.OPERATOR) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Operator accounts cannot be registered");
        }
        return parsed;
    }

    // A restaurant account must give its restaurant's name and address (#144).
    // Any other role must leave both empty, since only restaurant owners get a restaurant.
    public static void checkRestaurantDetails(UserRole role, String restaurantName, String address) {
        boolean hasName = restaurantName != null && !restaurantName.isBlank();
        boolean hasAddress = address != null && !address.isBlank();

        if (role == UserRole.RESTAURANT) {
            if (!hasName || !hasAddress) {
                throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID,
                        "A restaurant account needs the restaurant's name and address");
            }
        } else if (hasName || hasAddress) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID,
                    "Only a restaurant account can have a restaurant name or address");
        }
    }

    // The restaurant a new restaurant account owns: CLOSED, empty menu, no id yet (the database gives it one).
    // No coordinates yet, sign-up doesn't ask for them.
    public static Restaurant newRestaurant(String restaurantName) {
        return new Restaurant(null, restaurantName.trim(), RestaurantStatus.CLOSED, 0, 0, List.of());
    }
}