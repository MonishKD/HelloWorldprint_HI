package com.fortunategoods.application.ord;

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

import com.fortunategoods.application.ord.orders.OrderController;
import com.fortunategoods.application.ord.orders.OrderService;
import com.fortunategoods.application.ord.orders.OrderServiceResult;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.testsupport.SecuredControllerTest;

import static com.fortunategoods.testsupport.TestData.loggedIn;

/*
    This tests the REST API HTTP Endpoint
*/
@SecuredControllerTest(OrderController.class)
class OperatorCancelOrderControllerTest {

    private static final String URL = "/api/orders/100/operator-cancel";

    @Autowired private MockMvc mvc;
    @MockitoBean private OrderService service;
    @MockitoBean private CommandLogRepository logs;

    @Test
    void operatorCanCancel() throws Exception {
        when(service.operatorCancelOrder("9", UserRole.OPERATOR, "100")).thenReturn(new OrderServiceResult("100", OrderStatus.CANCELLED, 701));

        mvc.perform(post(URL).with(loggedIn(9, UserRole.OPERATOR)).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.orderId").value("100"))
        .andExpect(jsonPath("$.data.status").value("cancelled"))
        .andExpect(jsonPath("$.message").value("Order cancelled successfully by operator"))
        .andExpect(jsonPath("$.eventId").value(701));

        verify(service).operatorCancelOrder("9", UserRole.OPERATOR, "100");
    }

    @Test
    void guestsAndOtherRolesAreRefused() throws Exception {
        mvc.perform(post(URL).with(csrf())).andExpect(status().isUnauthorized());

        for (UserRole role : List.of(UserRole.CUSTOMER, UserRole.RESTAURANT, UserRole.COURIER)) {
            mvc.perform(post(URL).with(loggedIn(7, role)).with(csrf())).andExpect(status().isForbidden());
        }

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"INVALID,400", "NOT_FOUND,404", "CONFLICT,409"})
    void serviceRefusalsUseSharedErrors(DomainRuleViolation.Reason reason, int httpStatus) throws Exception {

        when(service.operatorCancelOrder("9", UserRole.OPERATOR, "100")).thenThrow(new DomainRuleViolation(reason, "Cancellation refused"));

        mvc.perform(post(URL)
        .with(loggedIn(9, UserRole.OPERATOR))
        .with(csrf()))
        .andExpect(status().is(httpStatus))
        .andExpect(jsonPath("$.code").value(reason.name()))
        .andExpect(jsonPath("$.message").value("Cancellation refused"));
    }
}