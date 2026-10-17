package com.fortunategoods.application.ord;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrderServiceResult;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.technical.security.SessionValidator;

public class CancelOrderCommandTest {
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
    void customerCanCancel() {
        Session session = new Session(7, UserRole.CUSTOMER);

        // Run the cancelOrder command and ensure it succeeds
        when(service.cancelOrder("7", "100")).thenReturn(new OrderServiceResult("100", OrderStatus.CANCELLED, 701));

        CancelOrderCommand command = new CancelOrderCommand(service, session, "100");

        CommandSuccess<CancelOrderResponse> result = invoker.process(command, session);

        // Check if order ID, eventIds, status and messages are matches
        assertEquals("100", result.getData().getOrderId());
        assertEquals("cancelled", result.getData().getStatus());
        assertEquals("Order cancelled.", result.getMsg());
        assertEquals(Long.valueOf(701), result.getEventId());

        verify(validator).requireValid(session);
        verify(validator).requireRoles(session, Set.of(UserRole.CUSTOMER));
        verify(service).cancelOrder("7", "100");
        verify(logs).save(any(CommandLog.class));
    }

    @Test
    void nonCustomerCannotCancel() {
        Session session = new Session(7, UserRole.RESTAURANT);

        CancelOrderCommand command = new CancelOrderCommand(service, session, "100");

        // Check if command can only be executed by customers
        assertEquals(Set.of(UserRole.CUSTOMER),command.validRoles());

        // Throw exception for the defined restaurant session and customer cancelOrder command
        doThrow(new AccessDeniedException("Forbidden")).when(validator).requireRoles(session, command.validRoles());

        assertThrows(AccessDeniedException.class, () -> invoker.process(command, session));

        verifyNoInteractions(service);
        verify(logs).save(any(CommandLog.class));
    }

    @Test
    void missingSessionCannotCancel() {
        CancelOrderCommand command = new CancelOrderCommand(service, null, "100");

        // Throw exception when null session
        doThrow(new AuthenticationCredentialsNotFoundException("Sign in required")).when(validator).requireValid(null);

        assertThrows(AuthenticationCredentialsNotFoundException.class,() -> invoker.process(command, null));

        verifyNoInteractions(service);
        verify(logs).save(any(CommandLog.class));
    }

    @Test
    void nonPlacedOrder() {
        Session session = new Session(7, UserRole.CUSTOMER);

        when(service.cancelOrder("7", "100")).thenThrow(new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Only PLACED orders can be cancelled"));
        
        CancelOrderCommand command = new CancelOrderCommand(service, session, "100");

        CommandExecutionException error = assertThrows(CommandExecutionException.class,() -> invoker.process(command, session));

        assertEquals(409, error.getStatus());
        assertEquals("CONFLICT", error.getCode());

        verify(service).cancelOrder("7", "100");
        verify(logs).save(any(CommandLog.class));
    }

    @Test
    void orderOfAnotherCustomer() {
        Session session = new Session(7, UserRole.CUSTOMER);

        when(service.cancelOrder("7", "100")).thenThrow(new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN, "You are only allowed to cancel your own order"));

        CancelOrderCommand command = new CancelOrderCommand(service, session, "100");

        assertThrows(AccessDeniedException.class, () -> invoker.process(command, session));

        verify(service).cancelOrder("7", "100");
        verify(logs).save(any(CommandLog.class));
    }
}
