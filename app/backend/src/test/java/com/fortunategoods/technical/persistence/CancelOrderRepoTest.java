package com.fortunategoods.technical.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fortunategoods.domain.order.OrderStatus;

public class CancelOrderRepoTest {
    private JdbcTemplate jdbc;
    private OrderEventRepository events;
    private OrderRepository orders;

    @BeforeEach
    void setup() {
        jdbc = mock(JdbcTemplate.class);
        events = mock(OrderEventRepository.class);

        orders = new OrderRepository(jdbc, events, mock(MenuItemRepository.class));
    }

    @Test
    void successfulCancellationCreatesOneRepoOrderEvent() {
        // Update one DB order
        when(jdbc.update(anyString(), eq("CANCELLED"), eq(100), eq("PLACED"))).thenReturn(1);

        when(events.add("100", "700", OrderStatus.PLACED, OrderStatus.CANCELLED)).thenReturn("701");

        String eventId = orders.updateStatusIfUnchanged("100", "700", OrderStatus.PLACED, OrderStatus.CANCELLED);

        assertEquals("701", eventId);

        verify(events).add("100", "700", OrderStatus.PLACED, OrderStatus.CANCELLED);

        verifyNoMoreInteractions(events);
    }

    @Test
    void refusedCancellationCreatesNoRepoOrderEvent() {
        // No order was updated because its status already changed.
        when(jdbc.update(anyString(), eq("CANCELLED"), eq(100), eq("PLACED"))).thenReturn(0);

        String eventId = orders.updateStatusIfUnchanged("100", "700", OrderStatus.PLACED, OrderStatus.CANCELLED);
        assertNull(eventId);
        verifyNoInteractions(events);
    }
}
