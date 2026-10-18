package com.fortunategoods.application.ord;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandLog;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.ord.restaurants.RestaurantCommands;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.application.ord.restaurants.RestaurantService;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.domain.restaurant.RestaurantSummary;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.technical.security.SessionValidator;

class BrowseRestaurantsCommandTest {

    // Fake RestaurantService: always returns one open restaurant.
    private final RestaurantService restaurantService = new RestaurantService(null) {
        @Override
        public List<RestaurantSummary> browseRestaurants() {
            return List.of(new RestaurantSummary("1", "Cafe", RestaurantStatus.OPEN, 45.5, -73.6));
        }
    };

    // Fake SessionValidator with the same behaviour as the real one: no session or a wrong role is refused.
    private final SessionValidator validator = new SessionValidator() {
        // Not used by these tests: they pass the Session in themselves.
        @Override
        public Session currentSession() {
            return null;
        }

        @Override
        public void requireValid(Session session) {
            if (session == null) {
                throw new AuthenticationCredentialsNotFoundException("not signed in");
            }
        }

        @Override
        public void requireRoles(Session session, Set<UserRole> allowedRoles) {
            if (!allowedRoles.contains(session.getRole())) {
                throw new AccessDeniedException("wrong role");
            }
        }
    };

    private final RestaurantCommands restaurantCommands =
            new RestaurantCommands(new CommandInvoker(validator, new CommandLogRepository(null) { public void save(CommandLog log) { } }), restaurantService);

    // A signed-in customer gets the list.
    @Test
    void customerGetsTheList() {
        CommandSuccess<List<RestaurantSummary>> result = restaurantCommands.browseRestaurants(new Session(7, UserRole.CUSTOMER));

        assertEquals(1, result.getData().size());
        assertEquals("Cafe", result.getData().get(0).name());
    }

    // No session (guest) is refused, so guests receive no data.
    @Test
    void guestIsRefused() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> restaurantCommands.browseRestaurants(null));
    }

    // Signed in, but not a customer.
    @Test
    void otherRolesAreRefused() {
        assertThrows(AccessDeniedException.class, () -> restaurantCommands.browseRestaurants(new Session(8, UserRole.RESTAURANT)));
        assertThrows(AccessDeniedException.class, () -> restaurantCommands.browseRestaurants(new Session(9, UserRole.COURIER)));
        assertThrows(AccessDeniedException.class, () -> restaurantCommands.browseRestaurants(new Session(9, UserRole.OPERATOR)));
    }

}
