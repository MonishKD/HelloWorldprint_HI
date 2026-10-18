package com.fortunategoods.application.ord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.access.AccessDeniedException;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandLog;
import com.fortunategoods.application.ord.orders.OrderService;
import com.fortunategoods.application.ord.orders.OrderServiceResult;
import com.fortunategoods.application.ord.orders.acceptance.AcceptOrderCommand;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.technical.security.SessionValidator;

import java.util.Set;

public class AcceptOrderCommandTest {
    private final OrderService service = mock(OrderService.class);
    private final SessionValidator validator = mock(SessionValidator.class);
    private final CommandLogRepository logs = mock(CommandLogRepository.class);
    private final CommandInvoker invoker = new CommandInvoker(validator, logs);

    @Test
    void staffCanAccept() {
        Session session = new Session(8, UserRole.RESTAURANT);
        when(service.acceptOrder("8", UserRole.RESTAURANT, "100")).thenReturn(new OrderServiceResult("100", OrderStatus.ACCEPTED, 701));

        var result = invoker.process(new AcceptOrderCommand(service, session, "100"), session);

        assertEquals("100", result.getData().getOrderId());
        assertEquals("accepted", result.getData().getStatus());
        assertEquals(Long.valueOf(701), result.getEventId());
        verify(validator).requireValid(session);
        verify(validator).requireRoles(session, Set.of(UserRole.RESTAURANT));
        verify(service).acceptOrder("8", UserRole.RESTAURANT, "100");
        verify(logs).save(any(CommandLog.class));
    }

    @Test
    void customerCannotAccept() {
        Session session = new Session(7, UserRole.CUSTOMER);
        AcceptOrderCommand command = new AcceptOrderCommand(service, session, "100");

        assertEquals(Set.of(UserRole.RESTAURANT), command.validRoles());
        doThrow(new AccessDeniedException("Forbidden")).when(validator).requireRoles(session, command.validRoles());

        assertThrows(AccessDeniedException.class,() -> invoker.process(command, session));

        verifyNoInteractions(service);
        verify(logs).save(any(CommandLog.class));
    }

    @Test
    void missingSessionCannotAccept() {
        doThrow(new AuthenticationCredentialsNotFoundException("Sign in required")).when(validator).requireValid(null);

        assertThrows(AuthenticationCredentialsNotFoundException.class,() -> invoker.process(new AcceptOrderCommand(service, null, "100"), null));

        verifyNoInteractions(service);
        verify(logs).save(any(CommandLog.class));
    }
}
