package com.fortunategoods.application.ord;

import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fortunategoods.application.ord.orders.RestaurantOrderController;
import com.fortunategoods.application.ord.orders.RestaurantOrderService;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.testsupport.SecuredControllerTest;
import static com.fortunategoods.testsupport.TestData.loggedIn;
import static com.fortunategoods.testsupport.TestData.order;

@SecuredControllerTest(RestaurantOrderController.class)
class RestaurantOrderControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private RestaurantOrderService service;
    @MockitoBean private CommandLogRepository logs;

    @Test
    void returnsPendingOrders() throws Exception {
        when(service.getPendingOrders("8", UserRole.RESTAURANT, "1"))
            .thenReturn(List.of(order("100", OrderStatus.PLACED), order("101", OrderStatus.PLACED)));

        mvc.perform(get("/api/restaurants/1/orders").param("status", "placed").with(loggedIn(8, UserRole.RESTAURANT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].orderId").value("100"))
            .andExpect(jsonPath("$.data[1].orderId").value("101"))
            .andExpect(jsonPath("$.data[0].status").value("placed"))
            .andExpect(jsonPath("$.data[0].instructions").value("No onions"))
            .andExpect(jsonPath("$.data[0].lines[0].menuItemId").value("10"))
            .andExpect(jsonPath("$.data[0].lines[0].name").value("Wrap"))
            .andExpect(jsonPath("$.data[0].lines[0].quantity").value(2));
    }

    @Test
    void emptyListAndDefaultStatusWork() throws Exception {
        when(service.getPendingOrders("8", UserRole.RESTAURANT, "1")).thenReturn(List.of());

        mvc.perform(get("/api/restaurants/1/orders").with(loggedIn(8, UserRole.RESTAURANT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty());
        verify(service).getPendingOrders("8", UserRole.RESTAURANT, "1");
    }

    @Test
    void missingSessionWrongRoleAndUnsupportedStatusAreRefused() throws Exception {
        mvc.perform(get("/api/restaurants/1/orders"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/restaurants/1/orders").with(loggedIn(8, UserRole.CUSTOMER)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/restaurants/1/orders").param("status", "accepted").with(loggedIn(8, UserRole.RESTAURANT)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void anotherRestaurantIsForbidden() throws Exception {
        when(service.getPendingOrders("8", UserRole.RESTAURANT, "2"))
            .thenThrow(new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN, "You can only view your own restaurant's orders"));

        mvc.perform(get("/api/restaurants/2/orders").with(loggedIn(8, UserRole.RESTAURANT)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.message").value("You can only view your own restaurant's orders"));
    }
}
