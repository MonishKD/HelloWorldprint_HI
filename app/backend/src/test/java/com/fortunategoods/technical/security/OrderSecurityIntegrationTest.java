package com.fortunategoods.technical.security;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.orders.OrderController;
import com.fortunategoods.application.ord.OrderExceptionHandler;
import com.fortunategoods.application.ord.orders.OrderService;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.application.ord.orders.OrderServiceResult;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.technical.security.role.RoleCheck;
import com.fortunategoods.technical.security.session.LoggedInUser;
import com.fortunategoods.technical.security.session.SpringSecuritySessionValidator;

import tools.jackson.databind.json.JsonMapper;

@SpringJUnitWebConfig(OrderSecurityIntegrationTest.TestConfig.class)
@TestPropertySource(properties = {"app.cors.allowed-origin=http://localhost:5173"})
public class OrderSecurityIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private OrderService service;

    @Autowired
    private CommandLogRepository logs;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        reset(service, logs);

        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void noSession401() throws Exception {
        mvc.perform(post("/api/orders/100/cancel")
        .with(csrf()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.message").value("Sign-in again to perform this action"));
        verifyNoInteractions(service);
    }

    @Test
    void restaurantCannotCancel() throws Exception {
        mvc.perform(post("/api/orders/100/cancel")
        .with(loggedIn(UserRole.RESTAURANT)).with(csrf()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.message").value("You are not allowed to perform this action"));

        verifyNoInteractions(service);
    }

    @Test
    void ownershipRefusal403() throws Exception {
        when(service.cancelOrder("7", "100")).thenThrow(new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN, "You are only allowed to cancel your own order"));

        mvc.perform(post("/api/orders/100/cancel")
        .with(loggedIn(UserRole.CUSTOMER)).with(csrf()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.message").value("You are only allowed to cancel your own order"));
        verify(service).cancelOrder("7", "100");
    }

    @Test
    void authorizedCustomerCanCancel() throws Exception {
        when(service.cancelOrder("7", "100")).thenReturn(new OrderServiceResult("100", OrderStatus.CANCELLED, 701));

        mvc.perform(post("/api/orders/100/cancel")
        .with(loggedIn(UserRole.CUSTOMER)).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.orderId").value("100"))
        .andExpect(jsonPath("$.data.status").value("cancelled"))
        .andExpect(jsonPath("$.eventId").value(701));
        verify(service).cancelOrder("7", "100");
    }

    // Creates the authenticated identity our real validator expects.
    private RequestPostProcessor loggedIn(UserRole role) {
        LoggedInUser user = new LoggedInUser(7, role);
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role.name());
        UsernamePasswordAuthenticationToken login = UsernamePasswordAuthenticationToken.authenticated(user, null, List.of(authority));
        return authentication(login);
    }

    // Load the real web/security components, without starting a database.
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({
        SecurityConfig.class,
        SecurityErrorResponses.class,
        SpringSecuritySessionValidator.class,
        RoleCheck.class,
        CommandInvoker.class,
        OrderController.class,
        OrderExceptionHandler.class
    })
    static class TestConfig {

        @Bean
        OrderService orderService() {
            return mock(OrderService.class);
        }

        @Bean
        CommandLogRepository commandLogRepository() {
            return mock(CommandLogRepository.class);
        }

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }
}