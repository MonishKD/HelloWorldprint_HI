package com.fortunategoods.application.reg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.domain.user.User;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.security.AuthenticationService;

// Registration: RegController checks the input and the Domain rules, then calls AuthenticationService.
// AuthenticationService is faked: it remembers what it was asked to save and saves nothing.
class RegControllerTest {

    // What the fake AuthenticationService was last asked to do. null = it was never called.
    private String savedRole;
    private String savedUsername;
    private Restaurant savedRestaurant;
    private String savedAddress;

    private final AuthenticationService authenticationService = new AuthenticationService(null, null, null, null, null) {
        @Override
        public User registerCustomer(String username, String email, String password) {
            return save("CUSTOMER", username, email, password);
        }

        @Override
        public User registerCourier(String username, String email, String password) {
            return save("COURIER", username, email, password);
        }

        @Override
        public User registerRestaurant(String username, String email, String password, Restaurant newRestaurant, String address) {
            savedRestaurant = newRestaurant;
            savedAddress = address;
            return save("RESTAURANT", username, email, password);
        }
    };

    private final RegController regController = new RegController(authenticationService);

    // A customer is saved with the CUSTOMER role, and the result has no password.
    @Test
    void customerIsRegistered() {
        RegistrationResult result = regController.register(" alice ", "alice@mail.com", "secret", "customer", null, null);

        assertEquals("CUSTOMER", savedRole);
        assertEquals("alice", savedUsername); // spaces trimmed
        assertEquals(new RegistrationResult("5", "alice", "alice@mail.com", UserRole.CUSTOMER), result);
        assertFalse(result.toString().contains("secret"));
    }

    // A courier is saved with the COURIER role. The role can be typed in any case.
    @Test
    void courierIsRegistered() {
        RegistrationResult result = regController.register("carl", "carl@mail.com", "secret", "COURIER", null, null);

        assertEquals("COURIER", savedRole);
        assertEquals(UserRole.COURIER, result.role());
    }

    // A restaurant account comes with its own new restaurant: CLOSED, empty menu, with the given name and address.
    @Test
    void restaurantIsRegisteredWithAClosedRestaurant() {
        RegistrationResult result = regController.register("green", "green@mail.com", "secret", "restaurant", " Green Leaf ", " 1 Main St ");

        assertEquals("RESTAURANT", savedRole);
        assertEquals(UserRole.RESTAURANT, result.role());
        assertEquals("Green Leaf", savedRestaurant.getName());
        assertEquals(RestaurantStatus.CLOSED, savedRestaurant.getStatus());
        assertTrue(savedRestaurant.getMenu().isEmpty());
        assertEquals("1 Main St", savedAddress);
    }

    // Operators can't sign up, and nothing is saved.
    @Test
    void operatorIsRefused() {
        assertInvalid(() -> regController.register("op", "op@mail.com", "secret", "operator", null, null));
    }

    // A missing or unknown role is refused.
    @Test
    void missingOrUnknownRoleIsRefused() {
        assertInvalid(() -> regController.register("a", "a@mail.com", "secret", null, null, null));
        assertInvalid(() -> regController.register("a", "a@mail.com", "secret", "admin", null, null));
    }

    // Username, email and password are required.
    @Test
    void missingFieldsAreRefused() {
        assertInvalid(() -> regController.register(" ", "a@mail.com", "secret", "customer", null, null));
        assertInvalid(() -> regController.register("a", null, "secret", "customer", null, null));
        assertInvalid(() -> regController.register("a", "a@mail.com", "", "customer", null, null));
    }

    // A restaurant needs a name and an address. Other roles can't send them.
    @Test
    void restaurantFieldsOnlyForRestaurants() {
        assertInvalid(() -> regController.register("g", "g@mail.com", "secret", "restaurant", null, "1 Main St"));
        assertInvalid(() -> regController.register("g", "g@mail.com", "secret", "restaurant", "Green Leaf", " "));
        assertInvalid(() -> regController.register("a", "a@mail.com", "secret", "customer", "Green Leaf", null));
    }

    // Checks that the call is refused as INVALID and that AuthenticationService was never asked to save anything.
    private void assertInvalid(Runnable registration) {
        DomainRuleViolation e = assertThrows(DomainRuleViolation.class, registration::run);
        assertEquals(DomainRuleViolation.Reason.INVALID, e.getReason());
        assertNull(savedRole);
    }

    // Remembers what the fake was asked to save, and returns the account as if the database gave it id 5.
    private User save(String role, String username, String email, String password) {
        savedRole = role;
        savedUsername = username;
        return new User("5", username, email, null, "hash-of-" + password, UserRole.valueOf(role));
    }
}
