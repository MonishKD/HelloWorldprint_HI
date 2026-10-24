package com.fortunategoods.application.ord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fortunategoods.application.ord.orders.OrderController;
import com.fortunategoods.application.ord.orders.OrderService;
import com.fortunategoods.application.ord.orders.OrderServiceResult;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.testsupport.SecuredControllerTest;
import static com.fortunategoods.testsupport.TestData.loggedIn;

@SecuredControllerTest(OrderController.class)
class PrepareOrderControllerTest {

    private static final String URL = "/api/orders/100/prepare";

    @Autowired private MockMvc mvc;
    @MockitoBean private OrderService service;
    @MockitoBean private CommandLogRepository logs;

    @Test
    void staffCanPrepareOrder() throws Exception {
        when(service.prepareOrder("8", UserRole.RESTAURANT, "100")).thenReturn(new OrderServiceResult("100", OrderStatus.PREPARING, 701));

        mvc.perform(post(URL).with(csrf()).with(loggedIn(8, UserRole.RESTAURANT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderId").value("100"))
            .andExpect(jsonPath("$.data.status").value("preparing"))
            .andExpect(jsonPath("$.eventId").value(701));
        verify(service).prepareOrder("8", UserRole.RESTAURANT, "100");
    }

    @Test
    void missingSessionWrongRoleAndMissingCsrfAreRefused() throws Exception {
        mvc.perform(post(URL).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post(URL).with(csrf()).with(loggedIn(8, UserRole.CUSTOMER))).andExpect(status().isForbidden());
        mvc.perform(post(URL).with(loggedIn(8, UserRole.RESTAURANT))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    // FORBIDDEN: another restaurant. CONFLICT: not first in the queue, or not accepted.
    @ParameterizedTest
    @CsvSource({"FORBIDDEN, 403", "CONFLICT, 409"})
    void serviceRefusalsKeepTheirStatus(DomainRuleViolation.Reason reason, int httpStatus) throws Exception {
        when(service.prepareOrder("8", UserRole.RESTAURANT, "100")).thenThrow(new DomainRuleViolation(reason, "Action refused"));

        mvc.perform(post(URL).with(csrf()).with(loggedIn(8, UserRole.RESTAURANT)))
            .andExpect(status().is(httpStatus))
            .andExpect(jsonPath("$.code").value(reason.name()))
            .andExpect(jsonPath("$.message").value("Action refused"));
    }
}
