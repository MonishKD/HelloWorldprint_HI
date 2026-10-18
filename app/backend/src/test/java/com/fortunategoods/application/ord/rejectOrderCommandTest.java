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

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandLog;
import com.fortunategoods.application.ord.orders.OrderService;
import com.fortunategoods.application.ord.orders.OrderServiceResult;
import com.fortunategoods.application.ord.orders.rejection.RejectOrderCommand;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.technical.security.SessionValidator;
import com.fortunategoods.application.reg.Session;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;

public class rejectOrderCommandTest {
    private final OrderService service = mock(OrderService.class);
    private final SessionValidator validator = mock(SessionValidator.class);
    private final CommandLogRepository logs = mock(CommandLogRepository.class);
    private final CommandInvoker invoker = new CommandInvoker(validator, logs);

    @Test
    void staffCanReject() {
        Session session = new Session(8, UserRole.RESTAURANT);

        when(service.rejectOrder("8", UserRole.RESTAURANT, "100", "Sold out")).thenReturn(new OrderServiceResult("100", OrderStatus.REJECTED, 701, "Sold out"));

        var result = invoker.process(new RejectOrderCommand(service, session, "100", "Sold out"), session);

        assertEquals("100", result.getData().getOrderId());
        assertEquals("rejected", result.getData().getStatus());
        assertEquals("Sold out", result.getData().getRejectReason());
        assertEquals(Long.valueOf(701), result.getEventId());

        verify(validator).requireValid(session);
        verify(validator).requireRoles(session, Set.of(UserRole.RESTAURANT));
        verify(service).rejectOrder("8", UserRole.RESTAURANT, "100", "Sold out");
        verify(logs).save(any(CommandLog.class));
    }

    @Test
    void customerCannotReject() {
        Session session = new Session(7, UserRole.CUSTOMER);
        RejectOrderCommand command = new RejectOrderCommand(service, session, "100", "Sold out");

        assertEquals(Set.of(UserRole.RESTAURANT), command.validRoles());

        doThrow(new AccessDeniedException("Forbidden")).when(validator).requireRoles(session, command.validRoles());

        assertThrows(AccessDeniedException.class, () -> invoker.process(command, session));

        verifyNoInteractions(service);
        verify(logs).save(any(CommandLog.class));
    }

    @Test
    void missingSessionCannotReject() {
        doThrow(new AuthenticationCredentialsNotFoundException("Sign in required")).when(validator).requireValid(null);

        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> invoker.process(new RejectOrderCommand(service, null, "100", "Sold out"), null));

        verifyNoInteractions(service);
        verify(logs).save(any(CommandLog.class));
    }
}
