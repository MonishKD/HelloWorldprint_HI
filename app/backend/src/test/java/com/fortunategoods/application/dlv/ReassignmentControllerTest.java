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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fortunategoods.application.dlv.reassignment.ReassignOrderResponse;
import com.fortunategoods.application.dlv.reassignment.StalledOrderResponse;
import com.fortunategoods.application.ord.execution.CommandLog;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.testsupport.SecuredControllerTest;

import static com.fortunategoods.testsupport.TestData.loggedIn;

@SecuredControllerTest(DlvController.class)
class ReassignmentControllerTest {
    private static final String READ = "/api/operator/stalled-orders";
    private static final String WRITE = "/api/orders/100/reassign";
    @Autowired private MockMvc mvc;
    @MockitoBean private ReassignmentService service;
    @MockitoBean private DeliveryService delivery;
    @MockitoBean private DispatchService dispatch;
    @MockitoBean private CommandLogRepository logs;

    @Test
    void operatorCanReadStalledOrders() throws Exception {
        when(service.stalledOrders("9", UserRole.OPERATOR)).thenReturn(
            List.of(new StalledOrderResponse("100", "Cafe", "Carl")));

        mvc.perform(get(READ).with(loggedIn(9, UserRole.OPERATOR)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].orderId").value("100"))
            .andExpect(jsonPath("$.data[0].restaurantName").value("Cafe"))
            .andExpect(jsonPath("$.data[0].courierName").value("Carl"));
    }

    @Test
    void noStalledOrdersReturnsAnEmptyList() throws Exception {
        when(service.stalledOrders("9", UserRole.OPERATOR)).thenReturn(List.of());

        mvc.perform(get(READ).with(loggedIn(9, UserRole.OPERATOR)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void operatorCanReassign() throws Exception {
        when(service.reassignOrder("9", UserRole.OPERATOR, "100")).thenReturn(new ReassignOrderResponse("100", "2"));

        mvc.perform(post(WRITE).with(csrf()).with(loggedIn(9, UserRole.OPERATOR)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderId").value("100"))
            .andExpect(jsonPath("$.data.offeredCourierId").value("2"))
            .andExpect(jsonPath("$.data.dispatchStatus").value("offered"))
            .andExpect(jsonPath("$.message").value("Order offered to courier 2."));
        verify(service).reassignOrder("9", UserRole.OPERATOR, "100");
        verify(logs).save(argThat(log -> log.getResult() == CommandLog.Result.SUCCESS));
    }

    @Test
    void noCourierReturnsWaiting() throws Exception {
        when(service.reassignOrder("9", UserRole.OPERATOR, "100")).thenReturn(new ReassignOrderResponse("100", null));

        mvc.perform(post(WRITE).with(csrf()).with(loggedIn(9, UserRole.OPERATOR)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.dispatchStatus").value("waiting"));
    }

    @Test
    void missingSessionWrongRolesAndMissingCsrfAreRefused() throws Exception {
        mvc.perform(get(READ)).andExpect(status().isUnauthorized());
        mvc.perform(post(WRITE).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post(WRITE).with(loggedIn(9, UserRole.OPERATOR))).andExpect(status().isForbidden());
        for (UserRole role : UserRole.values()) {
            if (role == UserRole.OPERATOR) continue;
            mvc.perform(get(READ).with(loggedIn(9, role))).andExpect(status().isForbidden());
            mvc.perform(post(WRITE).with(csrf()).with(loggedIn(9, role))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"CONFLICT,409", "NOT_FOUND,404", "FORBIDDEN,403", "INVALID,400"})
    void refusalsKeepTheirStatus(DomainRuleViolation.Reason reason, int expected) throws Exception {
        when(service.reassignOrder("9", UserRole.OPERATOR, "100")).thenThrow(new DomainRuleViolation(reason, "Reassignment refused"));

        mvc.perform(post(WRITE).with(csrf()).with(loggedIn(9, UserRole.OPERATOR)))
            .andExpect(status().is(expected))
            .andExpect(jsonPath("$.code").value(reason.name()));
        verify(logs).save(argThat(log -> log.getResult() == CommandLog.Result.REFUSED));
    }
}
