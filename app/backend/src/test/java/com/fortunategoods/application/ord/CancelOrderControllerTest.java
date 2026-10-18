package com.fortunategoods.application.ord;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.orders.OrderController;
import com.fortunategoods.application.ord.orders.OrderService;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.application.ord.orders.OrderServiceResult;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.technical.security.SessionValidator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


public class CancelOrderControllerTest {
    private OrderService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(OrderService.class);

        SessionValidator validator = mock(SessionValidator.class);
        CommandLogRepository logs = mock(CommandLogRepository.class);

        when(validator.currentSession()).thenReturn(new Session(7, UserRole.CUSTOMER));

        CommandInvoker invoker = new CommandInvoker(validator, logs);

        mvc = MockMvcBuilders.standaloneSetup(new OrderController(invoker, service, validator)).setControllerAdvice(new OrderExceptionHandler()).build();
    }

    @Test
    void cancellation200() throws Exception {
        when(service.cancelOrder("7", "100")).thenReturn(new OrderServiceResult("100", OrderStatus.CANCELLED, 701));

        mvc.perform(post("/api/orders/100/cancel"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.orderId").value("100"))
        .andExpect(jsonPath("$.data.status").value("cancelled"))
        .andExpect(jsonPath("$.message").value("Order cancelled."))
        .andExpect(jsonPath("$.eventId").value(701));
        verify(service).cancelOrder("7", "100");
    }

    @Test
    void invalidOrderId400() throws Exception {
        when(service.cancelOrder("7", "abc")).thenThrow(new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Order ID is invalid"));

        mvc.perform(post("/api/orders/abc/cancel"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("INVALID"))
        .andExpect(jsonPath("$.message").value("Order ID is invalid"));
    }

    @Test
    void missingOrder404() throws Exception {
        when(service.cancelOrder("7", "100")).thenThrow(new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "Order not found"));

        mvc.perform(post("/api/orders/100/cancel"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Order not found"));
    }

    @Test
    void nonPlacedOrderReturns409() throws Exception {
        when(service.cancelOrder("7", "100")).thenThrow(new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Only PLACED orders can be cancelled"));

        mvc.perform(post("/api/orders/100/cancel"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("CONFLICT"))
        .andExpect(jsonPath("$.message")
        .value("Only PLACED orders can be cancelled"));
    }
}
