package com.fortunategoods.application.ord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fortunategoods.application.ord.orders.OrderService;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrderEvent;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.order.state.IllegalTransitionException;
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.OrderEventRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;
import com.fortunategoods.domain.order.Order;
import java.util.List;

public class RejectOrderServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderEventRepository events = mock(OrderEventRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final OrderService service = new OrderService(mock(RestaurantRepository.class), orders, events, users);

    private Order order;

    @BeforeEach
    void setup() {
        order = createOrder(OrderStatus.PLACED);
        when(orders.findById("100")).thenReturn(order);
        when(users.findRestaurantIdByUserId("8")).thenReturn("1");
        when(events.findByOrderId("100")).thenReturn(List.of(new OrderEvent("700", "100", null, null, OrderStatus.PLACED, LocalDateTime.now())));
    }

    @Test
    void rejectionSavesReasonAndIsTerminal() {
        when(orders.rejectOrderIfPlaced("100", "700", "Sold out")).thenReturn("701");

        var result = service.rejectOrder("8", UserRole.RESTAURANT, "100", "  Sold out  ");

        assertEquals(OrderStatus.REJECTED, result.getStatus());
        assertEquals("Sold out", result.getRejectReason());
        assertEquals("Sold out", order.getRejectReason());
        assertEquals(701L, result.getEventId());

        assertThrows(IllegalTransitionException.class, order::accept);
        assertThrows(IllegalTransitionException.class, order::startPreparing);
        assertThrows(IllegalTransitionException.class, order::markReady);
        assertThrows(IllegalTransitionException.class, order::cancel);
        assertThrows(IllegalTransitionException.class, () -> order.reject("Another reason"));

        assertFailure(DomainRuleViolation.Reason.CONFLICT, "Another reason");
        verify(orders, times(1)).rejectOrderIfPlaced("100", "700", "Sold out");
    }

    @Test
    void invalidReasonsAreRejected() {
        for (String reason : new String[] {null, "", "   ", "x".repeat(501)}) {
            assertFailure(DomainRuleViolation.Reason.INVALID, reason);
            assertEquals(OrderStatus.PLACED, order.getStatus());
            assertNull(order.getRejectReason());
        }
        verifyNothingSaved();
    }

    @Test
    void wrongRestaurantForbidden() {
        for (String restaurantId : new String[] {"2", null}) {
            when(users.findRestaurantIdByUserId("8")).thenReturn(restaurantId);
            assertFailure(DomainRuleViolation.Reason.FORBIDDEN, "Sold out");
        }
        assertEquals(OrderStatus.PLACED, order.getStatus());
        verifyNothingSaved();
    }

    @Test
    void customerCannotReject() {
        DomainRuleViolation error = assertThrows(DomainRuleViolation.class, () -> service.rejectOrder("7", UserRole.CUSTOMER, "100", "Sold out"));
        assertEquals(DomainRuleViolation.Reason.FORBIDDEN, error.getReason());
        verifyNoInteractions(orders, events, users);
    }

    @Test
    void missingOrderIsRejected() {
        when(orders.findById("100")).thenReturn(null);
        assertFailure(DomainRuleViolation.Reason.NOT_FOUND, "Sold out");
        verifyNothingSaved();
    }

    @Test
    void otherStatusesCannotBeRejected() {
        for (OrderStatus status : OrderStatus.values()) {
            if (status == OrderStatus.PLACED) {
                continue;
            }

            Order existing = createOrder(status);
            when(orders.findById("100")).thenReturn(existing);

            assertFailure(DomainRuleViolation.Reason.CONFLICT, "Sold out");
            assertEquals(status, existing.getStatus());
        }
        verifyNothingSaved();
    }

    @Test
    void concurrentChangeIsRejected() {
        assertFailure(DomainRuleViolation.Reason.CONFLICT, "Sold out");
        verify(orders).rejectOrderIfPlaced("100", "700", "Sold out");
    }

    private void assertFailure(DomainRuleViolation.Reason reason, String message) {
        DomainRuleViolation error = assertThrows(DomainRuleViolation.class, () -> service.rejectOrder("8", UserRole.RESTAURANT, "100", message));
        assertEquals(reason, error.getReason());
    }

    private void verifyNothingSaved() {
        verify(orders, never()).rejectOrderIfPlaced(anyString(), anyString(), anyString());
    }

    private Order createOrder(OrderStatus status) {
        MenuItem item = new MenuItem("10", "1", "Wrap", MenuItemStatus.AVAILABLE, 4.50);
        return new Order("100", "7", "1", null, status, null, null, null, null, null, List.of(OrderLine.fromMenuItem(item, 1)));
    }
}
