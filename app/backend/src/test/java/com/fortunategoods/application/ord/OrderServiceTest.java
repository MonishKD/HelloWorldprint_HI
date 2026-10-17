package com.fortunategoods.application.ord;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderEvent;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.order.OrderServiceResult;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.order.RequestedOrderLineItem;
import com.fortunategoods.domain.restaurant.MenuItem;
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.technical.persistence.OrderEventRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;

public class OrderServiceTest {
    private RestaurantRepository restaurants;
    private OrderRepository orders;
    private OrderEventRepository events;
    private OrderService service;

    @BeforeEach
    void setup() {
        restaurants = mock(RestaurantRepository.class);
        orders = mock(OrderRepository.class);
        events = mock(OrderEventRepository.class);

        service = new OrderService(restaurants, orders, events);

        givenRestaurant(RestaurantStatus.OPEN, MenuItemStatus.AVAILABLE);
    }

    @Test
    void validOrderIsSaved() {
        when(orders.place(any(Order.class))).thenReturn("100");

        when(events.findByOrderId("100")).thenReturn(List.of(new OrderEvent("700", "100", null, null, OrderStatus.PLACED, LocalDateTime.now())));

        OrderServiceResult result = service.placeOrder("7", "1", List.of(item("10", "2")), "No onions", "123 Main Street");

        assertEquals("100", result.getOrderId());
        assertEquals(OrderStatus.PLACED, result.getStatus());
        assertEquals(700L, result.getEventId());

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);

        verify(orders).place(saved.capture());

        Order order = saved.getValue();
        OrderLine line = order.getLines().get(0);

        assertEquals("7", order.getCustomerId());
        assertEquals("1", order.getRestaurantId());
        assertEquals(OrderStatus.PLACED, order.getStatus());
        assertEquals("No onions", order.getSpecialInstructions());
        assertEquals("123 Main Street", order.getDeliveryAddress());

        assertEquals(2, line.getQuantity());
        assertEquals("Wrap", line.getName());
        assertEquals(4.50, line.getPrice(), 0.001);
        assertEquals(8.00, line.getOriginalPrice(), 0.001);

        // OrderRepository already creates the first event.
        verify(events, never()).addFirst(anyString());
    }

    @Test
    void unavailableRestaurantOrItemIsRejected() {
        givenRestaurant(RestaurantStatus.CLOSED, MenuItemStatus.AVAILABLE);
        assertRejected(List.of(item("10", "1")));

        givenRestaurant(RestaurantStatus.PAUSED, MenuItemStatus.AVAILABLE);
        assertRejected(List.of(item("10", "1")));

        // Checks the current status loaded on submission.
        givenRestaurant(RestaurantStatus.OPEN, MenuItemStatus.SOLD_OUT);
        assertRejected(List.of(item("10", "1")));
    }

    @Test
    void invalidQuantitiesAreRejected() {
        BigDecimal[] quantities = {null, BigDecimal.ZERO, new BigDecimal("-1"), new BigDecimal("1.5"), new BigDecimal("2147483648")};

        for (BigDecimal quantity : quantities) {
            assertRejected(List.of(new RequestedOrderLineItem("10", quantity)));
        }
    }

    @Test
    void emptyNullAndDuplicateItemsAreRejected() {
        assertRejected(List.of());
        assertRejected(null);
        assertRejected(Collections.singletonList(null));
        assertRejected(List.of(item("10", "1"),item("10", "2")));
    }

    @Test
    void invalidOrOtherRestaurantItemIdsAreRejected() {
        String[] ids = {null, "", "abc", "0", "2147483648"};

        for (String id : ids) {
            assertRejected(List.of(item(id, "1")));
        }
        assertRejected(List.of(item("10", "1"), item("999", "1")));
    }

    @Test
    void missingRestaurantIsRejected() {
        when(restaurants.findById("1")).thenReturn(null);

        DomainRuleViolation error = assertRejected(List.of(item("10", "1")));

        assertEquals(DomainRuleViolation.Reason.NOT_FOUND,error.getReason());
    }

    @Test
    void invalidCustomerOrRestaurantIdIsRejected() {
        String[] invalidIds = {null, "", "abc", "0", "2147483648"};

        for (String id : invalidIds) {
            assertThrows(DomainRuleViolation.class,() -> service.placeOrder(id, "1", List.of(item("10", "1")), null, null));

            assertThrows(DomainRuleViolation.class, () -> service.placeOrder("7", id, List.of(item("10", "1")), null, null));
        }

        verifyNoInteractions(restaurants, orders, events);
    }

    @Test
    void longInstructionsOrAddressAreRejected() {
        List<RequestedOrderLineItem> items = List.of(item("10", "1"));

        assertThrows(DomainRuleViolation.class, () -> service.placeOrder("7", "1", items, "a".repeat(501), null));

        assertThrows(DomainRuleViolation.class, () -> service.placeOrder("7", "1", items, null, "a".repeat(256)));

        verifyNoInteractions(orders, events);
    }

    // Checks refusal and confirms that nothing reaches persistence.
    private DomainRuleViolation assertRejected(List<RequestedOrderLineItem> items) {

        DomainRuleViolation error = assertThrows(DomainRuleViolation.class, () -> service.placeOrder("7", "1", items, null, null));

        verifyNoInteractions(orders, events);
        return error;
    }

    // Creates one requested item.
    private RequestedOrderLineItem item(String id, String quantity) {

        return new RequestedOrderLineItem(id, new BigDecimal(quantity));
    }

    // Gives the service a restaurant with one current menu item.
    private void givenRestaurant(RestaurantStatus restaurantStatus, MenuItemStatus itemStatus) {

        MenuItem item = new MenuItem("10", "1", "Wrap", itemStatus, 4.50, 8.00);

        Restaurant restaurant = new Restaurant("1", "Cafe", restaurantStatus, 1, 2, List.of(item));

        when(restaurants.findById("1")).thenReturn(restaurant);
    }
}
