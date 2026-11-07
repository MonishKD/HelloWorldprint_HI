package com.fortunategoods.application.prc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fortunategoods.application.ord.orders.OrderServiceResult;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.testsupport.SecuredControllerTest;

import static com.fortunategoods.testsupport.TestData.loggedIn;

@SecuredControllerTest(PrcOrderController.class)
class RefundControllerTest {
    private static final String URL = "/api/orders/100/refund";

    @Autowired private MockMvc mvc;
    @MockitoBean private PaymentService service;
    @MockitoBean private LedgerService ledger;
    @MockitoBean private CommandLogRepository logs;

    @Test
    void operatorCanRefund() throws Exception {
        when(service.refundOrder(UserRole.OPERATOR, "100")).thenReturn(new OrderServiceResult("100", OrderStatus.REFUNDED, 701));

        mvc.perform(post(URL).with(loggedIn(9, UserRole.OPERATOR)).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("refunded"))
            .andExpect(jsonPath("$.eventId").value(701));
    }

    @Test
    void otherRolesAreRefused() throws Exception {
        mvc.perform(post(URL).with(loggedIn(7, UserRole.CUSTOMER)).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
