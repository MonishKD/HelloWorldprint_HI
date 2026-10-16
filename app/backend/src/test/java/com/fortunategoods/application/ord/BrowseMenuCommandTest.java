package com.fortunategoods.application.ord;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.restaurant.MenuService;
import com.fortunategoods.domain.restaurant.RestaurantMenu;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.technical.security.SessionValidator;

class BrowseMenuCommandTest {

    // Fake MenuService: returns an empty menu for restaurant "1", nothing else is called.
    private final MenuService menuService = new MenuService(null, null) {
        @Override
        public RestaurantMenu browseMenu(String restaurantId, String userId, UserRole role) {
            return new RestaurantMenu(restaurantId, "Cafe", RestaurantStatus.OPEN, List.of());
        }
    };

    // Fake SessionValidator with the same behaviour as the real one: no session or a wrong role is refused.
    private final SessionValidator validator = new SessionValidator() {
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

    // The real CommandInvoker, with the fake validator and a command log that saves nothing.
    private final MenuCommands menuCommands = new MenuCommands(new CommandInvoker(validator, new CommandLogRepository(null) { public void save(CommandLog log) { } }), menuService);

    // A signed-in customer is allowed.
    @Test
    void customerGetsTheMenu() {
        CommandSuccess<RestaurantMenu> result = menuCommands.browseMenu(new Session(7, UserRole.CUSTOMER), "1");

        assertEquals("1", result.getData().restaurantId());
    }

    // A signed-in restaurant staff user is allowed.
    @Test
    void restaurantStaffGetsTheMenu() {
        CommandSuccess<RestaurantMenu> result = menuCommands.browseMenu(new Session(8, UserRole.RESTAURANT), "1");

        assertEquals("1", result.getData().restaurantId());
    }

    // No session (guest) is refused before the command runs.
    @Test
    void guestIsRefused() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> menuCommands.browseMenu(null, "1"));
    }

    // Signed in, but with a role that may not browse menus.
    @Test
    void courierAndOperatorAreRefused() {
        assertThrows(AccessDeniedException.class, () -> menuCommands.browseMenu(new Session(9, UserRole.COURIER), "1"));
        assertThrows(AccessDeniedException.class, () -> menuCommands.browseMenu(new Session(9, UserRole.OPERATOR), "1"));
    }

}
