package com.fortunategoods.application.ord;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.context.ApplicationEventPublisher;

import com.fortunategoods.application.ord.orders.OrderService;
import com.fortunategoods.application.dlv.DispatchService;
import com.fortunategoods.application.ord.orders.OrderServiceResult;
import com.fortunategoods.application.ord.orders.preparation.RegisterPreparationQueue;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderEvent;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.order.state.IllegalTransitionException;
import com.fortunategoods.technical.persistence.OrderEventRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;
import static com.fortunategoods.testsupport.TestData.order;

public class CancelOrderServiceTest {
    private OrderRepository orders;
    private OrderEventRepository events;
    private OrderService service;
    private Order order;
    private final RegisterPreparationQueue preparationQueue = mock(RegisterPreparationQueue.class);

    @BeforeEach
    void setup() {
        orders = mock(OrderRepository.class);
        events = mock(OrderEventRepository.class);

        service = new OrderService(mock(RestaurantRepository.class), orders, events, mock(UserRepository.class), preparationQueue, mock(ApplicationEventPublisher.class), mock(DispatchService.class));

        order = order("100", OrderStatus.PLACED);
        when(orders.findById("100")).thenReturn(order);

        OrderEvent event = new OrderEvent("700", "100", null, null, OrderStatus.PLACED, LocalDateTime.now());

        when(events.findByOrderId("100")).thenReturn(List.of(event));
    }

    @Test
    void customerCanCancel() {
        when(orders.updateStatusIfUnchanged("100", "700", OrderStatus.PLACED, OrderStatus.CANCELLED)).thenReturn("701");

        OrderServiceResult result = service.cancelOrder("7", "100");

        assertEquals("100", result.getOrderId());
        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        assertEquals(701L, result.getEventId());
        assertEquals(OrderStatus.CANCELLED, order.getStatus());

        verify(orders).updateStatusIfUnchanged("100", "700", OrderStatus.PLACED, OrderStatus.CANCELLED);
    }

    @Test
    void anotherCustomerCannotCancel() {
        DomainRuleViolation error = assertThrows(DomainRuleViolation.class, () -> service.cancelOrder("8", "100"));

        assertEquals(DomainRuleViolation.Reason.FORBIDDEN, error.getReason());

        assertEquals(OrderStatus.PLACED, order.getStatus());
        verifyNothingSaved();
    }

    @Test
    void otherStatusesCannotBeCancelled() {
        for (OrderStatus status : OrderStatus.values()) {
            if (status == OrderStatus.PLACED) {
                continue;
            }

            // Create order with status OTHER than PLACED and try to cancel order
            Order existingOrder = order("100", status);
            when(orders.findById("100")).thenReturn(existingOrder);

            DomainRuleViolation error = assertThrows(DomainRuleViolation.class, () -> service.cancelOrder("7", "100"));

            assertEquals(DomainRuleViolation.Reason.CONFLICT, error.getReason());
            assertEquals(status, existingOrder.getStatus());
        }

        verifyNothingSaved();
    }

    @Test
    void missingOrderIsRejected() {
        when(orders.findById("100")).thenReturn(null);

        DomainRuleViolation error = assertThrows(DomainRuleViolation.class, () -> service.cancelOrder("7", "100"));

        assertEquals(DomainRuleViolation.Reason.NOT_FOUND, error.getReason());

        verifyNothingSaved();
    }

    @Test
    void databaseChangeIsRejected() {
        // The mocked update returns null:
        // another request already changed the database order.
        DomainRuleViolation error = assertThrows(DomainRuleViolation.class, () -> service.cancelOrder("7", "100"));

        assertEquals(DomainRuleViolation.Reason.CONFLICT,error.getReason());
        assertEquals("Order changed before it could be cancelled", error.getMessage());
    }

    @Test
    void missingEventHistoryIsRejected() {
        when(events.findByOrderId("100")).thenReturn(List.of());

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> service.cancelOrder("7", "100"));

        assertEquals("Order has no event history", error.getMessage());
        verifyNothingSaved();
    }

    @Test
    void changedEventHistoryIsRejected() {
        OrderEvent accepted = new OrderEvent("701", "100", null, OrderStatus.PLACED,
            OrderStatus.ACCEPTED, LocalDateTime.now());
        when(events.findByOrderId("100")).thenReturn(List.of(accepted));

        DomainRuleViolation error = assertThrows(DomainRuleViolation.class,
            () -> service.cancelOrder("7", "100"));

        assertEquals(DomainRuleViolation.Reason.CONFLICT, error.getReason());
        assertEquals("Order is no longer placed", error.getMessage());
        verifyNothingSaved();
    }

    private void verifyNothingSaved() {
        verify(orders, never()).updateStatusIfUnchanged(anyString(), anyString(), any(), any());
    }

    @Test
    void cancelledOrderCannotChangeAgain() {
        when(orders.updateStatusIfUnchanged("100", "700", OrderStatus.PLACED, OrderStatus.CANCELLED)).thenReturn("701");

        service.cancelOrder("7", "100");

        assertEquals(OrderStatus.CANCELLED, order.getStatus());

        assertThrows(IllegalTransitionException.class, order::accept);
        assertThrows(IllegalTransitionException.class, order::startPreparing);
        assertThrows(IllegalTransitionException.class, order::markReady);
        assertThrows(IllegalTransitionException.class, order::reject);
        assertThrows(IllegalTransitionException.class, order::cancel);

        assertEquals(OrderStatus.CANCELLED, order.getStatus());

        // Second cancellation request refused.
        assertThrows(DomainRuleViolation.class, () -> service.cancelOrder("7", "100"));

        // DB updates only the first cancellation
        verify(orders).updateStatusIfUnchanged("100", "700",OrderStatus.PLACED, OrderStatus.CANCELLED);
    }
}
