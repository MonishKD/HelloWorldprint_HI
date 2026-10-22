package com.fortunategoods.application.ord;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandLog;
import com.fortunategoods.application.ord.menu.MenuService;
import com.fortunategoods.application.ord.menu.MenuController;
import com.fortunategoods.application.ord.restaurants.RestaurantService;
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;
import com.fortunategoods.technical.security.SecurityConfig;
import com.fortunategoods.technical.security.SecurityErrorResponses;
import com.fortunategoods.technical.security.role.RoleCheck;
import com.fortunategoods.technical.security.session.LoggedInUser;
import com.fortunategoods.technical.security.session.SpringSecuritySessionValidator;

// Test for controller that returns a restaurant's menu, with fake repositories instead of the database. The controller is read-only, so no changes are made to the data.
@WebMvcTest(
    controllers = MenuController.class,
    properties = {
        "app.cors.allowed-origin=http://localhost:5173",
        "spring.application.name=fortunategoods-test"
    }
)
@Import({
    SecurityConfig.class, SecurityErrorResponses.class,
    SpringSecuritySessionValidator.class, RoleCheck.class,
    CommandInvoker.class, OrderExceptionHandler.class,
    BrowseMenuControllerTest.FakeDatabase.class
})
class BrowseMenuControllerTest {

    @Autowired
    private MockMvc mvc;

    // A signed-in customer gets each item's name, price and availability, plus the restaurant's status.
    @Test
    void customerGetsTheMenu() throws Exception {
        mvc.perform(get("/api/restaurants/1/menu").with(loggedIn(UserRole.CUSTOMER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.restaurantName").value("Cafe"))
            .andExpect(jsonPath("$.data.restaurantStatus").value("OPEN"))
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].name").value("Wrap"))
            .andExpect(jsonPath("$.data.items[0].price").value(4.50))
            .andExpect(jsonPath("$.data.items[0].status").value("AVAILABLE"))
            .andExpect(jsonPath("$.data.items[1].status").value("SOLD_OUT"))
            .andExpect(jsonPath("$.message").value("Menu of Cafe loaded"));
    }

    // Staff can view their own restaurant's menu.
    @Test
    void staffGetsTheirOwnMenu() throws Exception {
        mvc.perform(get("/api/restaurants/1/menu").with(loggedIn(UserRole.RESTAURANT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.restaurantId").value("1"));
    }

    // Staff can't view another restaurant's menu.
    @Test
    void staffOfAnotherRestaurantIsForbidden() throws Exception {
        mvc.perform(get("/api/restaurants/2/menu").with(loggedIn(UserRole.RESTAURANT)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // Guests get a 401, roles that may not browse get a 403.
    @Test
    void guestAndCourierAreRefused() throws Exception {
        mvc.perform(get("/api/restaurants/1/menu"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/api/restaurants/1/menu").with(loggedIn(UserRole.COURIER)))
            .andExpect(status().isForbidden());
    }

    // An unknown restaurant is a 404, a malformed id a 400.
    @Test
    void unknownRestaurantAndBadIdAreRefused() throws Exception {
        mvc.perform(get("/api/restaurants/99/menu").with(loggedIn(UserRole.CUSTOMER)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(get("/api/restaurants/abc/menu").with(loggedIn(UserRole.CUSTOMER)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID"));
    }

    // Signs the request in as user 8 with this role, the way Login saves it in the session.
    private RequestPostProcessor loggedIn(UserRole role) {
        var login = UsernamePasswordAuthenticationToken.authenticated(new LoggedInUser(8, role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(login);
    }

    // The beans the controller needs, with fake repositories instead of the database.
    @TestConfiguration
    static class FakeDatabase {

        @Bean
        MenuService menuService() {
            // Restaurant "1" (Cafe) with one available and one sold-out item. No other restaurant exists.
            RestaurantRepository restaurants = new RestaurantRepository(null, null) {
                @Override
                public Restaurant findById(String id) {
                    if (!id.equals("1")) {
                        return null;
                    }
                    return new Restaurant("1", "Cafe", RestaurantStatus.OPEN, 45.5, -73.6, List.of(
                            new MenuItem("10", "1", "Wrap", MenuItemStatus.AVAILABLE, 4.50),
                            new MenuItem("11", "1", "Soup", MenuItemStatus.SOLD_OUT, 3.00)));
                }
            };
            // User 8 works for restaurant "1".
            UserRepository users = new UserRepository(null) {
                @Override
                public String findRestaurantIdByUserId(String userId) {
                    return userId.equals("8") ? "1" : null;
                }
            };
            return new MenuService(restaurants, users, null);
        }

        // Not used by these tests, but RestaurantController needs one.
        @Bean
        RestaurantService restaurantService() {
            return new RestaurantService(null, null);
        }

        @Bean
        CommandLogRepository commandLogRepository() {
            return new CommandLogRepository(null) {
                @Override
                public void save(CommandLog log) { }
            };
        }
    }
}
