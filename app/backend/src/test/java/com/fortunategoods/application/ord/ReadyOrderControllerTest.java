package com.fortunategoods.application.ord;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandLog;
import com.fortunategoods.application.ord.orders.OrderController;
import com.fortunategoods.application.ord.orders.OrderService;
import com.fortunategoods.application.ord.orders.OrderServiceResult;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.technical.security.SecurityConfig;
import com.fortunategoods.technical.security.SecurityErrorResponses;
import com.fortunategoods.technical.security.role.RoleCheck;
import com.fortunategoods.technical.security.session.LoggedInUser;
import com.fortunategoods.technical.security.session.SpringSecuritySessionValidator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = OrderController.class,
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

class ReadyOrderControllerTest {
    private static final String URL = "/api/orders/100/ready";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderService service;

    @MockitoBean
    private CommandLogRepository logs;

    @Test
    void staffCanMarkReady() throws Exception {
        when(service.markAsReadyOrder("8", UserRole.RESTAURANT, "100")).thenReturn(new OrderServiceResult("100", OrderStatus.READY, 701));

        mvc.perform(post(URL).with(csrf()).with(loggedIn(UserRole.RESTAURANT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.orderId").value("100"))
        .andExpect(jsonPath("$.data.status").value("ready"))
        .andExpect(jsonPath("$.eventId").value(701))
        .andExpect(jsonPath("$.message").value("Order marked ready."));

        verify(service).markAsReadyOrder("8", UserRole.RESTAURANT, "100");
        verify(logs).save(any(CommandLog.class));
    }

    @Test
    void missingSessionAndWrongRoleAreRefused() throws Exception {
        mvc.perform(post(URL).with(csrf())).andExpect(status().isUnauthorized());

        mvc.perform(post(URL).with(csrf()).with(loggedIn(UserRole.CUSTOMER))).andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void missingCsrfTokenIsRefused() throws Exception {
        mvc.perform(post(URL).with(loggedIn(UserRole.RESTAURANT))).andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void anotherRestaurantReturns403() throws Exception {
        refused(DomainRuleViolation.Reason.FORBIDDEN, 403);
    }

    @Test
    void wrongStateReturns409() throws Exception {
        refused(DomainRuleViolation.Reason.CONFLICT, 409);
    }

    @Test
    void missingOrderReturns404() throws Exception {
        refused(DomainRuleViolation.Reason.NOT_FOUND, 404);
    }

    @Test
    void invalidRequestReturns400() throws Exception {
        refused(DomainRuleViolation.Reason.INVALID, 400);
    }

    private void refused(DomainRuleViolation.Reason reason, int expectedStatus) throws Exception {

        when(service.markAsReadyOrder("8", UserRole.RESTAURANT, "100")).thenThrow(new DomainRuleViolation(reason, "Action refused"));

        mvc.perform(post(URL).with(csrf()).with(loggedIn(UserRole.RESTAURANT)))
        .andExpect(status().is(expectedStatus))
        .andExpect(jsonPath("$.code").value(reason.name()))
        .andExpect(jsonPath("$.message").value("Action refused"));

        verify(logs).save(any(CommandLog.class));
    }

    private RequestPostProcessor loggedIn(UserRole role) {
        var login = UsernamePasswordAuthenticationToken.authenticated(new LoggedInUser(8, role), null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));

        return authentication(login);
    }
}