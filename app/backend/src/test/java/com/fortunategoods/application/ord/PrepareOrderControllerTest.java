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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fortunategoods.application.ord.execution.CommandInvoker;
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
class PrepareOrderControllerTest {

    private static final String URL = "/api/orders/100/prepare";

    @Autowired
    private MockMvc mvc;

    @MockitoBean private OrderService service;
    @MockitoBean private CommandLogRepository logs;

    @Test
    void staffCanPrepareOrder() throws Exception {
        when(service.prepareOrder("8", UserRole.RESTAURANT, "100")).thenReturn(new OrderServiceResult("100", OrderStatus.PREPARING, 701));

        mvc.perform(post(URL).with(csrf()).with(login(UserRole.RESTAURANT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.orderId").value("100"))
        .andExpect(jsonPath("$.data.status").value("preparing"))
        .andExpect(jsonPath("$.eventId").value(701));

        verify(service).prepareOrder("8", UserRole.RESTAURANT, "100");
    }

    @Test
    void missingSessionAndWrongRoleAreRefused() throws Exception {
        mvc.perform(post(URL).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post(URL).with(csrf()).with(login(UserRole.CUSTOMER))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void missingCsrfIsRefused() throws Exception {
        mvc.perform(post(URL).with(login(UserRole.RESTAURANT))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void anotherRestaurantReturns403() throws Exception {
        refused(DomainRuleViolation.Reason.FORBIDDEN, 403);
    }

    @Test
    void fifoOrStateConflictReturns409() throws Exception {
        refused(DomainRuleViolation.Reason.CONFLICT, 409);
    }

    private void refused(DomainRuleViolation.Reason reason, int httpStatus)throws Exception {
        when(service.prepareOrder("8", UserRole.RESTAURANT, "100")).thenThrow(new DomainRuleViolation(reason, "Action refused"));

        mvc.perform(post(URL).with(csrf()).with(login(UserRole.RESTAURANT)))
        .andExpect(status().is(httpStatus))
        .andExpect(jsonPath("$.code").value(reason.name()))
        .andExpect(jsonPath("$.message").value("Action refused"));
    }

    private RequestPostProcessor login(UserRole role) {
        var login = UsernamePasswordAuthenticationToken.authenticated(
            new LoggedInUser(8, role), null,
            List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
        return authentication(login);
    }
}