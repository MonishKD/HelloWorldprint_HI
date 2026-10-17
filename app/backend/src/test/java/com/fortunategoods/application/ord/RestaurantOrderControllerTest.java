package com.fortunategoods.application.ord;

import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.restaurant.MenuItem;
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.technical.security.SecurityConfig;
import com.fortunategoods.technical.security.SecurityErrorResponses;
import com.fortunategoods.technical.security.role.RoleCheck;
import com.fortunategoods.technical.security.session.LoggedInUser;
import com.fortunategoods.technical.security.session.SpringSecuritySessionValidator;

@WebMvcTest(
    controllers = RestaurantOrderController.class,
    properties = "app.cors.allowed-origin=http://localhost:5173"
)
@Import({
    SecurityConfig.class, SecurityErrorResponses.class,
    SpringSecuritySessionValidator.class, RoleCheck.class,
    CommandInvoker.class, OrderExceptionHandler.class
})

public class RestaurantOrderControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RestaurantOrderService service;

    @MockitoBean
    private CommandLogRepository logs;

    @Test
    void returnsPendingOrders() throws Exception {
        when(service.getPendingOrders("8", UserRole.RESTAURANT, "1"))
        .thenReturn(List.of(order("100"), order("101")));

        mvc.perform(get("/api/restaurants/1/orders").param("status", "placed").with(loggedIn(UserRole.RESTAURANT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].orderId").value("100"))
        .andExpect(jsonPath("$.data[1].orderId").value("101"))
        .andExpect(jsonPath("$.data[0].status").value("placed"))
        .andExpect(jsonPath("$.data[0].instructions").value("No onions"))
        .andExpect(jsonPath("$.data[0].lines[0].menuItemId").value("10"))
        .andExpect(jsonPath("$.data[0].lines[0].name").value("Wrap"))
        .andExpect(jsonPath("$.data[0].lines[0].quantity").value(2));

        verify(service).getPendingOrders("8", UserRole.RESTAURANT, "1");
    }

    @Test
    void emptyListAndDefaultStatusWork() throws Exception {
        when(service.getPendingOrders("8", UserRole.RESTAURANT, "1")).thenReturn(List.of());

        mvc.perform(get("/api/restaurants/1/orders")
        .with(loggedIn(UserRole.RESTAURANT))).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());

        verify(service).getPendingOrders("8", UserRole.RESTAURANT, "1");
    }

    @Test
    void missingSessionAndWrongRoleAreRefused() throws Exception {
        mvc.perform(get("/api/restaurants/1/orders"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/api/restaurants/1/orders")
        .with(loggedIn(UserRole.CUSTOMER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(service);
    }

    @Test
    void anotherRestaurantIsForbidden() throws Exception {
        when(service.getPendingOrders("8", UserRole.RESTAURANT, "2"))
        .thenThrow(new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN, "You can only view your own restaurant's orders"));

        mvc.perform(get("/api/restaurants/2/orders").with(loggedIn(UserRole.RESTAURANT)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.message").value("You can only view your own restaurant's orders"));
    }

    @Test
    void unsupportedStatusIsRejected() throws Exception {
        mvc.perform(get("/api/restaurants/1/orders").param("status", "accepted").with(loggedIn(UserRole.RESTAURANT)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID"));

        verifyNoInteractions(service);
    }

    private RequestPostProcessor loggedIn(UserRole role) {
        var login = UsernamePasswordAuthenticationToken.authenticated(new LoggedInUser(8, role), null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(login);
    }

    private Order order(String id) {
        MenuItem item = new MenuItem("10", "1", "Wrap", MenuItemStatus.AVAILABLE, 4.50);
        return new Order(id, "7", "1", null, OrderStatus.PLACED, "No onions", null, null, null, null, List.of(OrderLine.fromMenuItem(item, 2)));
    }
}
