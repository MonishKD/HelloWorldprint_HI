package com.fortunategoods.application.dashboard;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderEvent;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.technical.persistence.OrderEventRepository;
import com.fortunategoods.technical.persistence.UserRepository;

class OrderDeliveryReaderTest {
    private static final LocalDateTime NOON = LocalDateTime.of(2026, 11, 8, 12, 0);

    private final UserRepository users = mock(UserRepository.class);
    private final OrderEventRepository events = mock(OrderEventRepository.class);
    private final OrderDeliveryReader reader = new OrderDeliveryReader(users, events);

    @Test
    void noCourierYetLeavesTheCourierFieldsEmpty() {
        OrderDelivery delivery = reader.of(order(null, OrderStatus.READY));

        assertNull(delivery.courierName());
        assertNull(delivery.pickedUpAt());
        assertEquals(45.5, delivery.deliveryLat());
        verifyNoInteractions(users, events);
    }

    @Test
    void readsTheCourierAndTheHandoffTimes() {
        when(users.findCourierName("5")).thenReturn("Carl");
        when(events.findByOrderId("100")).thenReturn(List.of(
                new OrderEvent("1", "100", null, OrderStatus.READY, OrderStatus.PICKED_UP, NOON),
                new OrderEvent("2", "100", "1", OrderStatus.PICKED_UP, OrderStatus.DELIVERED, NOON.plusMinutes(9))));

        OrderDelivery delivery = reader.of(order("5", OrderStatus.DELIVERED));

        assertEquals("Carl", delivery.courierName());
        assertEquals(NOON, delivery.pickedUpAt());
        assertEquals(NOON.plusMinutes(9), delivery.deliveredAt());
    }

    private Order order(String courierId, OrderStatus status) {
        MenuItem item = new MenuItem("10", "1", "Wrap", MenuItemStatus.AVAILABLE, 4.50);
        return new Order("100", "7", "1", courierId, status, null, "1 Main St", 45.5, -73.6, null, List.of(OrderLine.fromMenuItem(item, 1)));
    }
}
