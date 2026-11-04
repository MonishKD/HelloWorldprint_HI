package com.fortunategoods.application.ord;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import com.fortunategoods.domain.order.OrderFinished;
import org.springframework.context.ApplicationEventPublisher;

import com.fortunategoods.application.ord.orders.OrderService;
import com.fortunategoods.application.ord.orders.preparation.RegisterPreparationQueue;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderEvent;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.OrderEventRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;


import static com.fortunategoods.testsupport.TestData.order;

class OperatorCancelOrderServiceTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderEventRepository events = mock(OrderEventRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final OrderService service = new OrderService(mock(RestaurantRepository.class), orders, events,mock(UserRepository.class), mock(RegisterPreparationQueue.class), eventPublisher);

    @Test
    void operatorCanCancelAllThreeAllowedStates() {
        for (OrderStatus status : List.of(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY)) {
            Order existing = givenOrder(status);
            when(orders.updateStatusIfUnchanged("100", "700", status, OrderStatus.CANCELLED)).thenReturn("701");

            var result = service.operatorCancelOrder("9", UserRole.OPERATOR, "100");

            assertEquals("100", result.getOrderId());
            assertEquals(OrderStatus.CANCELLED, existing.getStatus());
            assertEquals(OrderStatus.CANCELLED, result.getStatus());
            assertEquals(701L, result.getEventId());

            verify(orders).updateStatusIfUnchanged("100", "700", status, OrderStatus.CANCELLED);
        }

        ArgumentCaptor<OrderFinished> captured = ArgumentCaptor.forClass(OrderFinished.class);
        verify(eventPublisher, times(3)).publishEvent(captured.capture());
        for (OrderFinished finished : captured.getAllValues()) {
            assertEquals("100", finished.getOrderId());
            assertEquals(OrderStatus.CANCELLED, finished.getFinalStatus());
        }
    }

    @Test
    void otherStatesAreRefused() {
        for (OrderStatus status : List.of(OrderStatus.PLACED, OrderStatus.PICKED_UP, OrderStatus.DELIVERED, OrderStatus.REJECTED, OrderStatus.CANCELLED, OrderStatus.REFUNDED)) {
            Order existing = givenOrder(status);

            var error = assertThrows(DomainRuleViolation.class,() -> service.operatorCancelOrder("9", UserRole.OPERATOR, "100"));
            assertEquals(DomainRuleViolation.Reason.CONFLICT, error.getReason());
            assertEquals(status, existing.getStatus());
        }

        verify(orders, never()).updateStatusIfUnchanged(anyString(), anyString(), any(), any());
    }

    @Test
    void customerStillCannotCancelAfterPlacement() {
        for (OrderStatus status : List.of(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY)) {
            givenOrder(status);

            var error = assertThrows(DomainRuleViolation.class,() -> service.cancelOrder("7", "100"));

            assertEquals(DomainRuleViolation.Reason.CONFLICT, error.getReason());
        }

        verify(orders, never()).updateStatusIfUnchanged(anyString(), anyString(), any(), any());
    }

    @Test
    void otherRolesAreRefusedBeforeReadingTheOrder() {
        for (UserRole role : List.of(UserRole.CUSTOMER, UserRole.RESTAURANT, UserRole.COURIER)) {
            var error = assertThrows(DomainRuleViolation.class,() -> service.operatorCancelOrder("9", role, "100"));

            assertEquals(DomainRuleViolation.Reason.FORBIDDEN, error.getReason());
        }
        verifyNoInteractions(orders, events);
    }

    @Test
    void missingOrderIdIsRefused() {
        var error = assertThrows(DomainRuleViolation.class,() -> service.operatorCancelOrder("9", UserRole.OPERATOR, "100"));
        assertEquals(DomainRuleViolation.Reason.NOT_FOUND, error.getReason());
        verifyNoInteractions(events);
    }

    @Test
    void invalidIdsAreRefused() {
        for (String invalid : new String[] {null, "", "abc", "0", "2147483648"}) {
            var orderError = assertThrows(DomainRuleViolation.class,() -> service.operatorCancelOrder("9", UserRole.OPERATOR, invalid));
            var operatorError = assertThrows(DomainRuleViolation.class,() -> service.operatorCancelOrder(invalid, UserRole.OPERATOR, "100"));

            assertEquals(DomainRuleViolation.Reason.INVALID, orderError.getReason());
            assertEquals(DomainRuleViolation.Reason.INVALID, operatorError.getReason());
        }

        verifyNoInteractions(orders, events);
    }

    @Test
    void missingOrChangedEventHistoryIsRefused() {
        givenOrder(OrderStatus.ACCEPTED);
        when(events.findByOrderId("100")).thenReturn(List.of());

        assertThrows(IllegalStateException.class,() -> service.operatorCancelOrder("9", UserRole.OPERATOR, "100"));

        givenOrder(OrderStatus.ACCEPTED);
        when(events.findByOrderId("100")).thenReturn(List.of(new OrderEvent("701", "100", "700", OrderStatus.ACCEPTED, OrderStatus.PREPARING, LocalDateTime.now())));

        var error = assertThrows(DomainRuleViolation.class,() -> service.operatorCancelOrder("9", UserRole.OPERATOR, "100"));

        assertEquals(DomainRuleViolation.Reason.CONFLICT, error.getReason());
        verify(orders, never()).updateStatusIfUnchanged(anyString(), anyString(), any(), any());
    }

    @Test
    void concurrentDatabaseChangeIsRefused() {
        givenOrder(OrderStatus.ACCEPTED);
        var error = assertThrows(DomainRuleViolation.class,() -> service.operatorCancelOrder("9", UserRole.OPERATOR, "100"));
        assertEquals(DomainRuleViolation.Reason.CONFLICT, error.getReason());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void orderIsReloadedAfterGettingTheLock() {
        Order before = givenOrder(OrderStatus.READY);
        when(orders.findById("100")).thenReturn(before, order("100", OrderStatus.PICKED_UP));

        var error = assertThrows(DomainRuleViolation.class,() -> service.operatorCancelOrder("9", UserRole.OPERATOR, "100"));

        assertEquals(DomainRuleViolation.Reason.CONFLICT, error.getReason());
        verify(orders).lockRestaurantQueue("1");
        verify(orders, never()).updateStatusIfUnchanged(anyString(), anyString(), any(), any());
    }

    // Helper method
    private Order givenOrder(OrderStatus status) {
        Order existing = order("100", status);
        when(orders.findById("100")).thenReturn(existing);
        when(events.findByOrderId("100")).thenReturn(List.of(new OrderEvent("700", "100", null, null, status, LocalDateTime.now())));
        return existing;
    }
}