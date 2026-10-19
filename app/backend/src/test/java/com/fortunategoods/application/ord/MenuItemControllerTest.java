package com.fortunategoods.application.ord;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandLog;
import com.fortunategoods.application.ord.menu.MenuController;
import com.fortunategoods.application.ord.menu.MenuService;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.technical.security.SecurityConfig;
import com.fortunategoods.technical.security.SecurityErrorResponses;
import com.fortunategoods.technical.security.role.RoleCheck;
import com.fortunategoods.technical.security.session.LoggedInUser;
import com.fortunategoods.technical.security.session.SpringSecuritySessionValidator;

// The menu item routes, through the real security, CommandInvoker and MenuService.
// Only the database is faked (FakeMenuDatabase): restaurant "1" with item "10", staff user 8 works there.
// Every request that changes something sends Spring's CSRF token with csrf(), like the browser does.
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
    MenuItemControllerTest.FakeBeans.class
})
class MenuItemControllerTest {

    private static final FakeMenuDatabase DB = new FakeMenuDatabase();
    private static final List<CommandLog> LOGS = new ArrayList<>();

    private static final String SALAD = """
        { "name": "Salad", "description": "Fresh greens", "price": 5.0, "originalPrice": 10.0, "status": "available" }
    """;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void startClean() {
        DB.reset();
        LOGS.clear();
    }

    @Test
    void addReturns201WithTheNewItem() throws Exception {
        mvc.perform(post("/api/restaurants/1/menu").with(staffOf1()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(SALAD))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/restaurants/1/menu/20"))
            .andExpect(jsonPath("$.data.id").value("20"))
            .andExpect(jsonPath("$.data.restaurantId").value("1"))
            .andExpect(jsonPath("$.data.name").value("Salad"))
            .andExpect(jsonPath("$.data.description").value("Fresh greens"))
            .andExpect(jsonPath("$.data.price").value(5.0))
            .andExpect(jsonPath("$.data.originalPrice").value(10.0))
            .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
            .andExpect(jsonPath("$.message").value("Menu item \"Salad\" added"));

        assertTrue(DB.items.containsKey("20"));
    }

    @Test
    void editReturns200WithTheEditedItem() throws Exception {
        mvc.perform(patch("/api/restaurants/1/menu/10").with(staffOf1()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"name\": \"Big Wrap\", \"price\": 6.0, \"status\": \"sold_out\" }"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Big Wrap"))
            .andExpect(jsonPath("$.data.status").value("SOLD_OUT"));

        assertEquals("Big Wrap", DB.items.get("10").getName());
    }

    @Test
    void removeReturns200AndHidesTheItem() throws Exception {
        mvc.perform(delete("/api/restaurants/1/menu/10").with(staffOf1()).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value("10"))
            .andExpect(jsonPath("$.message").value("Menu item \"Wrap\" removed"));

        assertTrue(DB.hidden.contains("10"));
    }

    @Test
    void guestAndCustomerAreRefused() throws Exception {
        mvc.perform(post("/api/restaurants/1/menu").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(SALAD))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(delete("/api/restaurants/1/menu/10").with(loggedIn(7, UserRole.CUSTOMER)).with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertEquals(2, DB.items.size());
        assertTrue(DB.hidden.isEmpty());
    }

    @Test
    void anotherRestaurantsMenuIsForbidden() throws Exception {
        mvc.perform(delete("/api/restaurants/2/menu/30").with(staffOf1()).with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("Restaurant staff can only change their own restaurant's menu"));

        assertFalse(DB.hidden.contains("30"));
    }

    @Test
    void invalidItemIs400AndLoggedAsRefused() throws Exception {
        mvc.perform(post("/api/restaurants/1/menu").with(staffOf1()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{ \"name\": \"Salad\", \"price\": 0, \"status\": \"available\" }"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID"))
            .andExpect(jsonPath("$.message").value("The price must be above zero"));

        assertEquals(2, DB.items.size());
        CommandLog last = LOGS.get(LOGS.size() - 1);
        assertEquals("AddMenuItemCommand", last.getCommandName());
        assertEquals(CommandLog.Result.REFUSED, last.getResult());
        assertEquals(400, last.getStatus());
    }

    @Test
    void unknownItemIs404() throws Exception {
        mvc.perform(patch("/api/restaurants/1/menu/99").with(staffOf1()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(SALAD))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(delete("/api/restaurants/1/menu/99").with(staffOf1()).with(csrf()))
            .andExpect(status().isNotFound());
    }

    private RequestPostProcessor staffOf1() {
        return loggedIn(8, UserRole.RESTAURANT);
    }

    private RequestPostProcessor loggedIn(long userId, UserRole role) {
        var login = UsernamePasswordAuthenticationToken.authenticated(new LoggedInUser(userId, role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(login);
    }

    @TestConfiguration
    static class FakeBeans {

        @Bean
        MenuService menuService() {
            return DB.menuService();
        }

        @Bean
        CommandLogRepository commandLogRepository() {
            return new CommandLogRepository(null) {
                @Override
                public void save(CommandLog log) {
                    LOGS.add(log);
                }
            };
        }
    }
}
