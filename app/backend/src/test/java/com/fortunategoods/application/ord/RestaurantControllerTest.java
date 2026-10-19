package com.fortunategoods.application.ord;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
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
import com.fortunategoods.application.ord.restaurants.RestaurantController;
import com.fortunategoods.application.ord.restaurants.RestaurantService;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.security.SecurityConfig;
import com.fortunategoods.technical.security.SecurityErrorResponses;
import com.fortunategoods.technical.security.role.RoleCheck;
import com.fortunategoods.technical.security.session.LoggedInUser;
import com.fortunategoods.technical.security.session.SpringSecuritySessionValidator;

@WebMvcTest(
    controllers = RestaurantController.class,
    properties = {
        "app.cors.allowed-origin=http://localhost:5173",
        "spring.application.name=fortunategoods-test"
    }
)
@Import({
    SecurityConfig.class, SecurityErrorResponses.class,
    SpringSecuritySessionValidator.class, RoleCheck.class,
    CommandInvoker.class, OrderExceptionHandler.class,
    RestaurantControllerTest.FakeDatabase.class
})
class RestaurantControllerTest {

    private static final List<Restaurant> STORED = new ArrayList<>();

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void emptyTheTable() {
        STORED.clear();
    }

    // A signed-in customer gets every restaurant with its id, name, status and coordinates.
    @Test
    void customerGetsTheList() throws Exception {
        STORED.add(new Restaurant("1", "Cafe", RestaurantStatus.OPEN, 45.5, -73.6, List.of()));
        STORED.add(new Restaurant("2", "Deli", RestaurantStatus.PAUSED, 45.4, -73.5, List.of()));
        STORED.add(new Restaurant("3", "Bakery", RestaurantStatus.CLOSED, 45.3, -73.4, List.of()));

        mvc.perform(get("/api/restaurants").with(loggedIn(UserRole.CUSTOMER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(3))
            .andExpect(jsonPath("$.data[0].id").value("1"))
            .andExpect(jsonPath("$.data[0].name").value("Cafe"))
            .andExpect(jsonPath("$.data[0].status").value("OPEN"))
            .andExpect(jsonPath("$.data[0].latitude").value(45.5))
            .andExpect(jsonPath("$.data[0].longitude").value(-73.6))
            .andExpect(jsonPath("$.data[1].status").value("PAUSED"))
            .andExpect(jsonPath("$.data[2].status").value("CLOSED"));
    }

    // No restaurants: a well-formed empty list.
    @Test
    void noRestaurantsGivesAnEmptyList() throws Exception {
        mvc.perform(get("/api/restaurants").with(loggedIn(UserRole.CUSTOMER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data").isEmpty());
    }

    // Guests get a 401, other roles a 403 (the list is for customers, #43).
    @Test
    void guestAndOtherRolesAreRefused() throws Exception {
        mvc.perform(get("/api/restaurants"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/api/restaurants").with(loggedIn(UserRole.COURIER)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // Signs the request in as user 7 with this role, the way Login saves it in the session.
    private RequestPostProcessor loggedIn(UserRole role) {
        var login = UsernamePasswordAuthenticationToken.authenticated(new LoggedInUser(7, role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(login);
    }

    // The beans the controller needs, with a fake repository instead of the database.
    @TestConfiguration
    static class FakeDatabase {

        @Bean
        RestaurantService restaurantService() {
            RestaurantRepository restaurants = new RestaurantRepository(null, null) {
                @Override
                public List<Restaurant> findAll() {
                    return STORED;
                }
            };
            return new RestaurantService(restaurants);
        }

        @Bean
        MenuService menuService() {
            return new MenuService(null, null, null);
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
