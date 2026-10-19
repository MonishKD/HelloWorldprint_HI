package com.fortunategoods.application.ord;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fortunategoods.application.ord.orders.OrderService;
import com.fortunategoods.application.ord.orders.RegisterPreparationQueue;
import com.fortunategoods.domain.order.*;
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReadyOrderServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderEventRepository events = mock(OrderEventRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final OrderService service = new OrderService(mock(RestaurantRepository.class), orders, events, users, mock(RegisterPreparationQueue.class));
    private Order order;

    @BeforeEach
    void setup() {
        order = createOrder(OrderStatus.PREPARING);
        when(orders.findById("100")).thenReturn(order);
        when(users.findRestaurantIdByUserId("8")).thenReturn("1");
        when(events.findByOrderId("100")).thenReturn(List.of(new OrderEvent("700", "100", "699", OrderStatus.ACCEPTED, OrderStatus.PREPARING, LocalDateTime.now())));
    }

    @Test
    void marksReadyOnlyOnce() {
        when(orders.updateStatusIfUnchanged("100", "700", OrderStatus.PREPARING, OrderStatus.READY)).thenReturn("701");

        var result = service.markAsReadyOrder("8", UserRole.RESTAURANT, "100");

        assertEquals("100", result.getOrderId());
        assertEquals(OrderStatus.READY, result.getStatus());
        assertEquals(OrderStatus.READY, order.getStatus());
        assertNull(order.getCourierId());
        assertEquals(701L, result.getEventId());
        fails(DomainRuleViolation.Reason.CONFLICT);

        verify(orders).updateStatusIfUnchanged("100", "700", OrderStatus.PREPARING, OrderStatus.READY);
    }

    @Test
    void customerIsForbidden() {
        var error = assertThrows(DomainRuleViolation.class, () -> service.markAsReadyOrder("7", UserRole.CUSTOMER, "100"));
        assertEquals(DomainRuleViolation.Reason.FORBIDDEN, error.getReason());
        verifyNoInteractions(orders, events, users);
    }

    @Test
    void wrongRestaurantOrNoAssociationIsForbidden() {
        for (String id : new String[] {"2", null}) {
            when(users.findRestaurantIdByUserId("8")).thenReturn(id);
            fails(DomainRuleViolation.Reason.FORBIDDEN);
            assertEquals(OrderStatus.PREPARING, order.getStatus());
        }
        nothingSaved();
    }

    @Test
    void missingOrderIsRejected() {
        when(orders.findById("100")).thenReturn(null);
        fails(DomainRuleViolation.Reason.NOT_FOUND);
        nothingSaved();
    }

    @Test
    void otherStatusesAreRejected() {
        for (OrderStatus status : OrderStatus.values()) {
            if (status == OrderStatus.PREPARING) continue;
            Order existing = createOrder(status);
            when(orders.findById("100")).thenReturn(existing);
            fails(DomainRuleViolation.Reason.CONFLICT);
            assertEquals(status, existing.getStatus());
        }
        nothingSaved();
    }

    @Test
    void concurrentChangeIsRejected() {
        // Mocked database update returns null.
        fails(DomainRuleViolation.Reason.CONFLICT);
        verify(orders).updateStatusIfUnchanged("100", "700", OrderStatus.PREPARING, OrderStatus.READY);
    }

    private void fails(DomainRuleViolation.Reason reason) {
        var error = assertThrows(DomainRuleViolation.class, () -> service.markAsReadyOrder("8", UserRole.RESTAURANT, "100"));
        assertEquals(reason, error.getReason());
    }

    private void nothingSaved() {
        verify(orders, never()).updateStatusIfUnchanged(anyString(), anyString(), any(), any());
        verifyNoInteractions(events);
    }

    private Order createOrder(OrderStatus status) {
        var item = new MenuItem("10", "1", "Wrap", MenuItemStatus.AVAILABLE, 4.50);
        return new Order("100", "7", "1", null, status, null, null, null, null, null, List.of(OrderLine.fromMenuItem(item, 1)));
    }
}
