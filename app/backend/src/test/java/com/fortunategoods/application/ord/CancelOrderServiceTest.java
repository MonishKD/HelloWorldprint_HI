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

import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderEvent;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.order.OrderServiceResult;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.order.StateDesignPattern.IllegalTransitionException;
import com.fortunategoods.domain.restaurant.MenuItem;
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.technical.persistence.OrderEventRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;

public class CancelOrderServiceTest {
    private OrderRepository orders;
    private OrderEventRepository events;
    private OrderService service;
    private Order order;

    @BeforeEach
    void setup() {
        orders = mock(OrderRepository.class);
        events = mock(OrderEventRepository.class);

        service = new OrderService(mock(RestaurantRepository.class), orders, events);

        order = createOrder(OrderStatus.PLACED);
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
            Order existingOrder = createOrder(status);
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
    }

    private void verifyNothingSaved() {
        verify(orders, never()).updateStatusIfUnchanged(anyString(), anyString(), any(), any());
    }

    private Order createOrder(OrderStatus status) {
        MenuItem item = new MenuItem("10", "1", "Wrap", MenuItemStatus.AVAILABLE, 4.50);
        return new Order("100", "7", "1", null, status, null, null, null, null, null, List.of(OrderLine.fromMenuItem(item, 1)));
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
