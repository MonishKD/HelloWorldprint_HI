package com.fortunategoods.application.dlv;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fortunategoods.application.ord.orders.OrderServiceResult;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.testsupport.SecuredControllerTest;

import static com.fortunategoods.testsupport.TestData.loggedIn;

@SecuredControllerTest(DlvController.class)
class DeliveryControllerTest {
    private static final String URL = "/api/couriers/me/order/";
    @Autowired private MockMvc mvc;
    @MockitoBean private DeliveryService service;
    @MockitoBean private DispatchService dispatch;
    @MockitoBean private CommandLogRepository logs;

    @Test
    void courierCanConfirmBothHandoffs() throws Exception {
        when(service.confirmPickup("15", UserRole.COURIER)).thenReturn(
            new OrderServiceResult("100", OrderStatus.PICKED_UP, 701));
        when(service.confirmDelivery("15", UserRole.COURIER)).thenReturn(
            new OrderServiceResult("100", OrderStatus.DELIVERED, 702));

        mvc.perform(post(URL + "pickup").with(csrf()).with(loggedIn(15, UserRole.COURIER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderId").value("100"))
            .andExpect(jsonPath("$.data.status").value("picked_up"))
            .andExpect(jsonPath("$.eventId").value(701))
            .andExpect(jsonPath("$.message").value("Order picked up."));
        mvc.perform(post(URL + "deliver").with(csrf()).with(loggedIn(15, UserRole.COURIER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("delivered"))
            .andExpect(jsonPath("$.eventId").value(702))
            .andExpect(jsonPath("$.message").value("Order delivered."));
        verify(service).confirmPickup("15", UserRole.COURIER);
        verify(service).confirmDelivery("15", UserRole.COURIER);
    }

    @Test
    void missingSessionWrongRolesAndMissingCsrfAreRefused() throws Exception {
        for (String action : List.of("pickup", "deliver")) {
            mvc.perform(post(URL + action).with(csrf())).andExpect(status().isUnauthorized());
            mvc.perform(post(URL + action).with(loggedIn(15, UserRole.COURIER)))
                .andExpect(status().isForbidden());
            for (UserRole role : UserRole.values()) {
                if (role == UserRole.COURIER) continue;
                mvc.perform(post(URL + action).with(csrf()).with(loggedIn(15, role)))
                    .andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"CONFLICT,409", "NOT_FOUND,404", "FORBIDDEN,403", "INVALID,400"})
    void refusalsKeepTheirHttpStatus(DomainRuleViolation.Reason reason, int expected) throws Exception {
        when(service.confirmPickup("15", UserRole.COURIER)).thenThrow(new DomainRuleViolation(reason, "Handoff refused"));
        when(service.confirmDelivery("15", UserRole.COURIER)).thenThrow(new DomainRuleViolation(reason, "Handoff refused"));

        for (String action : List.of("pickup", "deliver")) {
            mvc.perform(post(URL + action).with(csrf()).with(loggedIn(15, UserRole.COURIER)))
                .andExpect(status().is(expected))
                .andExpect(jsonPath("$.code").value(reason.name()));
        }
    }
}
