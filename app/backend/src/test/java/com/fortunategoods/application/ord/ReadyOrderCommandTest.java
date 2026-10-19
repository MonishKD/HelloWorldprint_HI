package com.fortunategoods.application.ord;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import com.fortunategoods.application.ord.execution.*;
import com.fortunategoods.application.ord.orders.*;
import com.fortunategoods.application.ord.orders.ready.*;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.technical.security.SessionValidator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReadyOrderCommandTest {
    private final OrderService service = mock(OrderService.class);
    private final SessionValidator validator = mock(SessionValidator.class);
    private final CommandLogRepository logs = mock(CommandLogRepository.class);
    private final CommandInvoker invoker = new CommandInvoker(validator, logs);

    @Test
    void staffCanMarkReady() {
        Session session = new Session(8, UserRole.RESTAURANT);

        when(service.markAsReadyOrder("8", UserRole.RESTAURANT, "100")).thenReturn(new OrderServiceResult("100", OrderStatus.READY, 701));

        var result = invoker.process(new ReadyOrderCommand(service, session, "100"), session);

        assertEquals("100", result.getData().getOrderId());
        assertEquals("ready", result.getData().getStatus());
        assertEquals("Order marked ready.", result.getMsg());
        assertEquals(Long.valueOf(701), result.getEventId());

        verify(validator).requireValid(session);
        verify(validator).requireRoles(session, Set.of(UserRole.RESTAURANT));
        verify(service).markAsReadyOrder("8", UserRole.RESTAURANT, "100");
        verify(logs).save(any(CommandLog.class));
    }

    @Test
    void customerCannotMarkReady() {
        Session session = new Session(7, UserRole.CUSTOMER);
        ReadyOrderCommand command = new ReadyOrderCommand(service, session, "100");

        assertEquals(Set.of(UserRole.RESTAURANT), command.validRoles());

        doThrow(new AccessDeniedException("Forbidden")).when(validator).requireRoles(session, command.validRoles());

        assertThrows(AccessDeniedException.class, () -> invoker.process(command, session));

        verifyNoInteractions(service);
        verify(logs).save(any(CommandLog.class));
    }

    @Test
    void missingSessionCannotMarkReady() {
        doThrow(new AuthenticationCredentialsNotFoundException("Sign in required")).when(validator).requireValid(null);

        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> invoker.process(new ReadyOrderCommand(service, null, "100"), null));

        verifyNoInteractions(service);
        verify(logs).save(any(CommandLog.class));
    }
}