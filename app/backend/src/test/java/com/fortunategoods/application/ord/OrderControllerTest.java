package com.fortunategoods.application.ord;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrderServiceResult;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.technical.security.SessionValidator;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/*
    This class tests the order controller REST Endpoint handling.
*/
public class OrderControllerTest {
    private OrderService service;
    private MockMvc mvc;

    private static final String BODY = """
        {
            "restaurantId": "1",
            "lines": [
                {"itemId": "10", "quantity": 2}
            ],
            "instructions": "No onions"
        }
    """;

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
    void validOrder201() throws Exception {
        when(service.placeOrder(eq("7"), eq("1"), anyList(), eq("No onions"), isNull())).thenReturn(new OrderServiceResult("100", OrderStatus.PLACED, 700));

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/orders/100"))
        .andExpect(jsonPath("$.data.orderId").value("100"))
        .andExpect(jsonPath("$.data.status").value("placed"))
        .andExpect(jsonPath("$.message").value("Order placed."))
        .andExpect(jsonPath("$.eventId").value(700));

        // Customer 7 comes from the session.
        // The submitted item and quantity must reach the service.
        verify(service).placeOrder(eq("7"), eq("1"),
        argThat(
            items -> items.size() == 1 && "10".equals(items.get(0).getMenuItemId())
            && items.get(0).getQuantity().intValueExact() == 2), 
            eq("No onions"),isNull());
    }

    @Test
    void malformedJson400() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID"));

        verifyNoInteractions(service);
    }

    @Test
    void invalidOrder400() throws Exception {
        when(service.placeOrder(anyString(), anyString(), anyList(), any(), any())).thenThrow(new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Quantity must be greater than zero"));

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID"))
        .andExpect(jsonPath("$.message").value("Quantity must be greater than zero"));
    }

    @Test
    void missingRestaurant404() throws Exception {
        when(service.placeOrder(anyString(), anyString(), anyList(), any(), any())).thenThrow(new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "Restaurant not found"));

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Restaurant not found"));
    }

    @Test
    void unavailableRestaurant409() throws Exception {
        when(service.placeOrder(anyString(), anyString(), anyList(), any(), any())).thenThrow(new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Restaurant is closed"));

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"))
        .andExpect(jsonPath("$.message").value("Restaurant is closed"));
    }
}
