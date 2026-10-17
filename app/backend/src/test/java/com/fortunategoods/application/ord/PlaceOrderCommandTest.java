package com.fortunategoods.application.ord;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.OrderServiceResult;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.order.RequestedOrderLineItem;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.technical.security.SessionValidator;

public class PlaceOrderCommandTest {
    private OrderService service;
    private SessionValidator validator;
    private CommandLogRepository logs;
    private CommandInvoker invoker;

    @BeforeEach
    void setup() {
        service = mock(OrderService.class);
        validator = mock(SessionValidator.class);
        logs = mock(CommandLogRepository.class);
        invoker = new CommandInvoker(validator, logs);
    }

    @Test
    void customerPlacesOrder() {
        Session session = new Session(7, UserRole.CUSTOMER);

        List<RequestedOrderLineItem> items = List.of(new RequestedOrderLineItem("10", new BigDecimal("2")));

        when(service.placeOrder("7", "1", items, "No onions", null)).thenReturn(new OrderServiceResult("100", OrderStatus.PLACED, 700));

        PlaceOrderCommand command = new PlaceOrderCommand(service, session, "1", items, "No onions", null);

        CommandSuccess<PlaceOrderResponse> result = invoker.process(command, session);

        assertEquals(Set.of(UserRole.CUSTOMER), command.validRoles());
        assertEquals("100", result.getData().getOrderId());
        assertEquals("placed", result.getData().getStatus());
        assertEquals("Order placed.", result.getMsg());
        assertEquals(Long.valueOf(700), result.getEventId());

        verify(validator).requireValid(session);
        verify(validator).requireRoles(session, command.validRoles());

        // Default verification means exactly one call.
        verify(service).placeOrder("7", "1", items, "No onions", null);

        verify(logs).save(argThat(log -> log.getResult() == CommandLog.Result.SUCCESS && Long.valueOf(700).equals(log.getEventId())));
    }

    @Test
    void wrongRoleCannotPlaceOrder() {
        Session session = new Session(8, UserRole.RESTAURANT);

        PlaceOrderCommand command = new PlaceOrderCommand(service, session, "1", List.of(), null, null);

        doThrow(new AccessDeniedException("Not allowed")).when(validator).requireRoles(session, command.validRoles());

        assertThrows(AccessDeniedException.class, () -> invoker.process(command, session));

        verifyNoInteractions(service);
        verify(logs).save(argThat(log -> log.getStatus() == 403));
    }

    @Test
    void missingSessionCannotPlaceOrder() {
        PlaceOrderCommand command = new PlaceOrderCommand(service, null, "1", List.of(), null, null);

        doThrow(new AuthenticationCredentialsNotFoundException("Sign in required")).when(validator).requireValid(null);

        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> invoker.process(command, null));

        verifyNoInteractions(service);
        verify(logs).save(argThat(log -> log.getStatus() == 401));
    }
}
