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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fortunategoods.application.ord.execution.CommandLog;
import com.fortunategoods.application.ord.menu.MenuController;
import com.fortunategoods.application.ord.menu.MenuService;
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.technical.persistence.MenuItemRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;
import com.fortunategoods.testsupport.SecuredControllerTest;
import static com.fortunategoods.testsupport.TestData.loggedIn;
import static com.fortunategoods.testsupport.TestData.wrap;

// Browsing and changing a menu through the real security, CommandInvoker and MenuService. Only the repositories are mocked:
// restaurant "1" (Cafe) has item "10" Wrap and a sold-out item "11" Soup, staff user 8 works there.
// Every request that changes something sends Spring's CSRF token with csrf(), like the browser does.
@SecuredControllerTest(MenuController.class)
@Import(MenuService.class)
class MenuControllerTest {

    private static final String SALAD = """
        { "name": "Salad", "description": "Fresh greens", "price": 5.0, "originalPrice": 10.0, "status": "available" }
    """;

    @Autowired private MockMvc mvc;
    @MockitoBean private MenuItemRepository items;
    @MockitoBean private RestaurantRepository restaurants;
    @MockitoBean private UserRepository users;
    @MockitoBean private CommandLogRepository logs;

    @BeforeEach
    void setup() {
        when(users.findRestaurantIdByUserId("8")).thenReturn("1");
        when(restaurants.findById("1")).thenReturn(new Restaurant("1", "Cafe", RestaurantStatus.OPEN, 45.5, -73.6,
                List.of(new MenuItem("10", "1", "Wrap", MenuItemStatus.AVAILABLE, 4.50),
                        new MenuItem("11", "1", "Soup", MenuItemStatus.SOLD_OUT, 3.00))));
        when(items.findByRestaurantId("1")).thenReturn(List.of(wrap()));
        when(items.add(eq("1"), any(), any(), any())).thenReturn("20");
    }

    @Test
    void customerGetsTheMenu() throws Exception {
        mvc.perform(get("/api/restaurants/1/menu").with(loggedIn(7, UserRole.CUSTOMER)))
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

    @Test
    void staffSeeOnlyTheirOwnMenu() throws Exception {
        mvc.perform(get("/api/restaurants/1/menu").with(staffOf1()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.restaurantId").value("1"));
        mvc.perform(get("/api/restaurants/2/menu").with(staffOf1()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void browsingRefusesGuestsCouriersUnknownAndBadIds() throws Exception {
        mvc.perform(get("/api/restaurants/1/menu"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/restaurants/1/menu").with(loggedIn(9, UserRole.COURIER)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/restaurants/99/menu").with(loggedIn(7, UserRole.CUSTOMER)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/restaurants/abc/menu").with(loggedIn(7, UserRole.CUSTOMER)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID"));
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
    void changesRefuseGuestsCustomersAndOtherRestaurants() throws Exception {
        mvc.perform(post("/api/restaurants/1/menu").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(SALAD))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(delete("/api/restaurants/1/menu/10").with(loggedIn(7, UserRole.CUSTOMER)).with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
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
        assertEquals("AddMenuItemCommand", logged.getValue().getCommandName());
        assertEquals(CommandLog.Result.REFUSED, logged.getValue().getResult());
        assertEquals(400, logged.getValue().getStatus());
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

    private static org.springframework.test.web.servlet.request.RequestPostProcessor staffOf1() {
        return loggedIn(8, UserRole.RESTAURANT);
    }
}
