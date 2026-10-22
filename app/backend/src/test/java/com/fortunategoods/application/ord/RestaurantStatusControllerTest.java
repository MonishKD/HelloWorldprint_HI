package com.fortunategoods.application.ord;

import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.verifyNoInteractions;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.menu.MenuService;
import com.fortunategoods.application.ord.restaurants.RestaurantController;
import com.fortunategoods.application.ord.restaurants.RestaurantService;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
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
    CommandInvoker.class, OrderExceptionHandler.class
})
class RestaurantStatusControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean private RestaurantService service;
    @MockitoBean private MenuService menus;
    @MockitoBean private CommandLogRepository logs;

    @Test
    void staffCanChangeStatus() throws Exception {
        Restaurant result = new Restaurant("1", "Cafe", RestaurantStatus.PAUSED, 45.5, -73.6, List.of());
        when(service.setRestaurantStatus("8", UserRole.RESTAURANT, "1", RestaurantStatus.PAUSED)).thenReturn(result);

        request("paused", UserRole.RESTAURANT)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.restaurantId").value("1"))
        .andExpect(jsonPath("$.data.status").value("paused"));
    }

    @Test
    void invalidStatusReturns400() throws Exception {
        request("invalid", UserRole.RESTAURANT).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void missingSessionAndWrongRoleAreRefused() throws Exception {
        mvc.perform(patch("/api/restaurants/1/status").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"paused\"}")).andExpect(status().isUnauthorized());
        request("paused", UserRole.CUSTOMER).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void serviceRefusalsReturn403And409() throws Exception {
        when(service.setRestaurantStatus("8", UserRole.RESTAURANT, "1", RestaurantStatus.OPEN
        )).thenThrow(new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN, "Another restaurant"
        )).thenThrow(new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Empty menu"));

        request("open", UserRole.RESTAURANT)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        request("open", UserRole.RESTAURANT)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    private org.springframework.test.web.servlet.ResultActions request(String status, UserRole role) throws Exception {
        return mvc.perform(patch("/api/restaurants/1/status")
        .with(csrf()).with(login(role))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"" + status + "\"}"));
    }

    private RequestPostProcessor login(UserRole role) {
        var login = UsernamePasswordAuthenticationToken.authenticated(
            new LoggedInUser(8, role), null,
            List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
        return authentication(login);
    }
}