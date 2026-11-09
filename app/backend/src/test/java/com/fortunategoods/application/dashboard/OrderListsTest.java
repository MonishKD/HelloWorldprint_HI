package com.fortunategoods.application.dashboard;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.json.JsonMapper;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.OrderEventRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.UserRepository;

// CustomerOrderList and RestaurantOrderList (#132): each reads only the signed-in user's own orders,
// and both shape them the same way for the dashboards.
public class OrderListsTest {

    private OrderRepository orders;
    private UserRepository users;
    private CustomerOrderList customerView;
    private RestaurantOrderList restaurantView;

    @BeforeEach
    void setup() {
        orders = mock(OrderRepository.class);
        users = mock(UserRepository.class);
        customerView = new CustomerOrderList(orders, new OrderDeliveryReader(users, mock(OrderEventRepository.class)));
        restaurantView = new RestaurantOrderList(orders, users);
    }

    @Test
    void customerSeesOnlyTheirOwnOrders() {
        when(orders.findByCustomer("7")).thenReturn(List.of(order("100", OrderStatus.PLACED)));

        List<DashboardOrder> result = customerView.ordersOf(new Session(7, UserRole.CUSTOMER));

        assertEquals(1, result.size());
        DashboardOrder first = result.get(0);
        assertEquals("100", first.orderId());
        assertEquals("placed", first.status());
        assertEquals("No onions", first.instructions());
        assertEquals("10", first.lines().get(0).itemId());
        assertEquals(7.0, first.lines().get(0).originalPrice());
        assertNull(first.lines().get(1).originalPrice()); // not on discount
        assertEquals(3.0 * 2 + 5.0, first.total());
        verify(orders).findByCustomer("7");
    }

    @Test
    void operatorJsonHasOneCourierName() {
        OperatorOrderResponse response = new OperatorOrderResponse(DashboardOrder.of(order("100", OrderStatus.READY)), "Bistro", "5", "Carl");

        String json = new JsonMapper().writeValueAsString(response);

        assertEquals(2, json.split("\"courierName\"", -1).length);
        assertTrue(json.contains("\"courierName\":\"Carl\""));
    }

    @Test
    void staffSeeTheirOwnRestaurantsOrdersOldestFirst() {
        when(users.findRestaurantIdByUserId("8")).thenReturn("1");
        when(orders.findByRestaurant("1")).thenReturn(List.of(order("100", OrderStatus.ACCEPTED), order("101", OrderStatus.READY)));

        List<DashboardOrder> result = restaurantView.ordersOf(new Session(8, UserRole.RESTAURANT));

        assertEquals(List.of("100", "101"), result.stream().map(DashboardOrder::orderId).toList());
        assertEquals("ready", result.get(1).status());
        verify(orders).findByRestaurant("1");
    }

    @Test
    void staffWithoutARestaurantSeeNothing() {
        when(users.findRestaurantIdByUserId("8")).thenReturn(null);

        assertTrue(restaurantView.ordersOf(new Session(8, UserRole.RESTAURANT)).isEmpty());
        verify(orders, never()).findByRestaurant(anyString());
    }

    private Order order(String id, OrderStatus status) {
        MenuItem quiche = new MenuItem("10", "1", "Quiche", MenuItemStatus.AVAILABLE, 3.0, 7.0);
        MenuItem soup = new MenuItem("11", "1", "Soup", MenuItemStatus.AVAILABLE, 5.0);
        return new Order(id, "7", "1", null, status, "No onions", "1 Main St", null, null, null,
                List.of(OrderLine.fromMenuItem(quiche, 2), OrderLine.fromMenuItem(soup, 1)));
    }
}
