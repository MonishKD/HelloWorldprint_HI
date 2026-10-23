package com.fortunategoods.application.ord;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.technical.persistence.MenuItemRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;
import com.fortunategoods.technical.security.SecurityConfig;
import com.fortunategoods.technical.security.SecurityErrorResponses;
import com.fortunategoods.technical.security.role.RoleCheck;
import com.fortunategoods.technical.security.session.LoggedInUser;
import com.fortunategoods.technical.security.session.SpringSecuritySessionValidator;

// The menu item routes, through the real security, CommandInvoker and MenuService. Only the repositories are mocked:
// restaurant "1" with item "10", staff user 8 works there.
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
    MenuService.class
})
class MenuItemControllerTest {

    private static final String SALAD = """
        { "name": "Salad", "description": "Fresh greens", "price": 5.0, "originalPrice": 10.0, "status": "available" }
    """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MenuItemRepository items;

    @MockitoBean
    private RestaurantRepository restaurants;

    @MockitoBean
    private UserRepository users;

    @MockitoBean
    private CommandLogRepository logs;

    @BeforeEach
    void setup() {
        when(users.findRestaurantIdByUserId("8")).thenReturn("1");
        when(items.findByRestaurantId("1")).thenReturn(List.of(new MenuItem("10", "1", "Wrap", MenuItemStatus.AVAILABLE, 4.50, 9.00)));
        when(items.add(eq("1"), any(), any(), any())).thenReturn("20");
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

        verify(items).add(eq("1"), any(), eq("Fresh greens"), eq(10.0));
    }

    @Test
    void editReturns200WithTheEditedItem() throws Exception {
        mvc.perform(patch("/api/restaurants/1/menu/10").with(staffOf1()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"name\": \"Big Wrap\", \"price\": 6.0, \"status\": \"sold_out\" }"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Big Wrap"))
            .andExpect(jsonPath("$.data.status").value("SOLD_OUT"));

        ArgumentCaptor<MenuItem> saved = ArgumentCaptor.forClass(MenuItem.class);
        verify(items).update(saved.capture(), any(), any());
        assertEquals("Big Wrap", saved.getValue().getName());
    }

    @Test
    void removeReturns200AndHidesTheItem() throws Exception {
        mvc.perform(delete("/api/restaurants/1/menu/10").with(staffOf1()).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value("10"))
            .andExpect(jsonPath("$.message").value("Menu item \"Wrap\" removed"));

        verify(items).hide("10");
    }

    @Test
    void guestAndCustomerAreRefused() throws Exception {
        mvc.perform(post("/api/restaurants/1/menu").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(SALAD))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(delete("/api/restaurants/1/menu/10").with(loggedIn(7, UserRole.CUSTOMER)).with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNothingSaved();
    }

    @Test
    void anotherRestaurantsMenuIsForbidden() throws Exception {
        mvc.perform(delete("/api/restaurants/2/menu/30").with(staffOf1()).with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("Restaurant staff can only change their own restaurant's menu"));

        verifyNothingSaved();
    }

    @Test
    void invalidItemIs400AndLoggedAsRefused() throws Exception {
        mvc.perform(post("/api/restaurants/1/menu").with(staffOf1()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{ \"name\": \"Salad\", \"price\": 0, \"status\": \"available\" }"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID"))
            .andExpect(jsonPath("$.message").value("The price must be above zero"));

        verifyNothingSaved();
        ArgumentCaptor<CommandLog> logged = ArgumentCaptor.forClass(CommandLog.class);
        verify(logs, atLeastOnce()).save(logged.capture());
        CommandLog last = logged.getValue();
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

        verifyNothingSaved();
    }

    private void verifyNothingSaved() {
        verify(items, never()).add(anyString(), any(), any(), any());
        verify(items, never()).update(any(), any(), any());
        verify(items, never()).hide(anyString());
    }

    private RequestPostProcessor staffOf1() {
        return loggedIn(8, UserRole.RESTAURANT);
    }

    private RequestPostProcessor loggedIn(long userId, UserRole role) {
        var login = UsernamePasswordAuthenticationToken.authenticated(new LoggedInUser(userId, role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(login);
    }
}
