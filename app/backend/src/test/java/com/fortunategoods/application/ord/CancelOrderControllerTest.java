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

// POST /api/orders/{id}/cancel through the real security and CommandInvoker. OrderService is mocked.
@SecuredControllerTest(OrderController.class)
class CancelOrderControllerTest {

    private static final String URL = "/api/orders/100/cancel";

    @Autowired private MockMvc mvc;
    @MockitoBean private OrderService service;
    @MockitoBean private CommandLogRepository logs;

    @Test
    void customerCanCancel() throws Exception {
        when(service.cancelOrder("7", "100")).thenReturn(new OrderServiceResult("100", OrderStatus.CANCELLED, 701));

        mvc.perform(post(URL).with(loggedIn(7, UserRole.CUSTOMER)).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderId").value("100"))
            .andExpect(jsonPath("$.data.status").value("cancelled"))
            .andExpect(jsonPath("$.message").value("Order cancelled."))
            .andExpect(jsonPath("$.eventId").value(701));
        verify(service).cancelOrder("7", "100");
    }

    @Test
    void guestGets401AndRestaurantGets403() throws Exception {
        mvc.perform(post(URL).with(csrf()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.message").value("Sign-in again to perform this action"));
        mvc.perform(post(URL).with(loggedIn(7, UserRole.RESTAURANT)).with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.message").value("You are not allowed to perform this action"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({
        "FORBIDDEN, 403, You are only allowed to cancel your own order",
        "INVALID,   400, Order ID is invalid",
        "NOT_FOUND, 404, Order not found",
        "CONFLICT,  409, Only PLACED orders can be cancelled"
    })
    void serviceRefusalsKeepTheirStatusAndMessage(DomainRuleViolation.Reason reason, int httpStatus, String message) throws Exception {
        when(service.cancelOrder("7", "100")).thenThrow(new DomainRuleViolation(reason, message));

        mvc.perform(post(URL).with(loggedIn(7, UserRole.CUSTOMER)).with(csrf()))
            .andExpect(status().is(httpStatus))
            .andExpect(jsonPath("$.status").value(httpStatus))
            .andExpect(jsonPath("$.code").value(reason.name()))
            .andExpect(jsonPath("$.message").value(message));
    }
}
