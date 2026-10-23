package com.fortunategoods.application.ord;

import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fortunategoods.application.ord.restaurants.RestaurantController;
import com.fortunategoods.application.ord.restaurants.RestaurantService;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.domain.restaurant.RestaurantSummary;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.testsupport.SecuredControllerTest;
import static com.fortunategoods.testsupport.TestData.loggedIn;

// The restaurant list and the open / paused / closed switch, through the real security and CommandInvoker.
// RestaurantService is mocked; its rules are tested in RestaurantServiceTest.
@SecuredControllerTest(RestaurantController.class)
class RestaurantControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private RestaurantService service;
    @MockitoBean private CommandLogRepository logs;

    @Test
    void customerGetsTheList() throws Exception {
        when(service.browseRestaurants("7", UserRole.CUSTOMER)).thenReturn(List.of(
                new RestaurantSummary("1", "Cafe", RestaurantStatus.OPEN, 45.5, -73.6),
                new RestaurantSummary("2", "Deli", RestaurantStatus.PAUSED, 45.4, -73.5),
                new RestaurantSummary("3", "Bakery", RestaurantStatus.CLOSED, 45.3, -73.4)));

        mvc.perform(get("/api/restaurants").with(loggedIn(7, UserRole.CUSTOMER)))
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

    @Test
    void noRestaurantsGivesAnEmptyList() throws Exception {
        when(service.browseRestaurants("7", UserRole.CUSTOMER)).thenReturn(List.of());

        mvc.perform(get("/api/restaurants").with(loggedIn(7, UserRole.CUSTOMER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void listRefusesGuestsAndOtherRoles() throws Exception {
        mvc.perform(get("/api/restaurants"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/restaurants").with(loggedIn(9, UserRole.COURIER)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/restaurants").with(loggedIn(8, UserRole.RESTAURANT)))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void staffCanChangeStatus() throws Exception {
        when(service.setRestaurantStatus("8", UserRole.RESTAURANT, "1", RestaurantStatus.PAUSED))
            .thenReturn(new Restaurant("1", "Cafe", RestaurantStatus.PAUSED, 45.5, -73.6, List.of()));

        setStatus("paused", UserRole.RESTAURANT)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.restaurantId").value("1"))
            .andExpect(jsonPath("$.data.status").value("paused"));
    }

    @Test
    void statusChangeRefusesBadStatusGuestsAndCustomers() throws Exception {
        setStatus("invalid", UserRole.RESTAURANT).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/restaurants/1/status").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"paused\"}"))
            .andExpect(status().isUnauthorized());
        setStatus("paused", UserRole.CUSTOMER).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void serviceRefusalsReturn403And409() throws Exception {
        when(service.setRestaurantStatus("8", UserRole.RESTAURANT, "1", RestaurantStatus.OPEN))
            .thenThrow(new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN, "Another restaurant"))
            .thenThrow(new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Empty menu"));

        setStatus("open", UserRole.RESTAURANT)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        setStatus("open", UserRole.RESTAURANT)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    private ResultActions setStatus(String newStatus, UserRole role) throws Exception {
        return mvc.perform(patch("/api/restaurants/1/status").with(csrf()).with(loggedIn(8, role))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + newStatus + "\"}"));
    }
}
