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

    // Every new account needs a real position; 0, 0 means "no location" on the map. A customer also needs a name and a delivery address.
    public static void checkLocation(UserRole role, SignUpLocation location) {
        Double latitude = location.latitude();
        Double longitude = location.longitude();
        if (latitude == null || longitude == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Choose your location on the map");
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180 || (latitude == 0 && longitude == 0)) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "That location is not valid");
        }
        if (role == UserRole.CUSTOMER) {
            requireText(location.name(), 100, "Your name");
            requireText(location.deliveryAddress(), 255, "Your delivery address");
        }
    }

    private static void requireText(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, label + " is required");
        }
        if (value.trim().length() > maxLength) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, label + " must be at most " + maxLength + " characters");
        }
    }

    // The restaurant a new restaurant account owns: CLOSED, empty menu, no id yet (the database gives it one).
    public static Restaurant newRestaurant(String restaurantName, SignUpLocation location) {
        return new Restaurant(null, restaurantName.trim(), RestaurantStatus.CLOSED, location.latitude(), location.longitude(), List.of());
    }
}
