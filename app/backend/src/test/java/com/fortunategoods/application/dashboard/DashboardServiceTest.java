package com.fortunategoods.application.dashboard;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortunategoods.application.ord.orders.RestaurantOrderService;
import com.fortunategoods.application.ord.orders.preparation.GetPreparationQueueResponse;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;

// The repositories are mocked: customer 7 has orders 101 and 100, staff 8 works for restaurant "1", staff 9 has none.
class DashboardServiceTest {

    private static final MenuItem WRAP = new MenuItem("10", "1", "Wrap", MenuItemStatus.AVAILABLE, 4.50, 9.00);

    private OrderRepository orders;
    private RestaurantOrderService restaurantOrders;
    private DashboardService dashboardService;

    @BeforeEach
    void setup() {
        orders = mock(OrderRepository.class);
        UserRepository users = mock(UserRepository.class);
        RestaurantRepository restaurants = mock(RestaurantRepository.class);
        restaurantOrders = mock(RestaurantOrderService.class);

        when(orders.findByCustomerId("7")).thenReturn(List.of(order("101", OrderStatus.PLACED, null), order("100", OrderStatus.REJECTED, "Sold out")));
        when(users.findRestaurantIdByUserId("8")).thenReturn("1");
        when(restaurants.findById("1")).thenReturn(new Restaurant("1", "Cafe", RestaurantStatus.OPEN, 45.5, -73.6, List.of()));
        when(restaurantOrders.getPreparationQueue("8", UserRole.RESTAURANT, "1")).thenReturn(List.of(
                new GetPreparationQueueResponse(1, "102", OrderStatus.ACCEPTED),
                new GetPreparationQueueResponse(2, "103", OrderStatus.ACCEPTED)));

        dashboardService = new DashboardService(orders, users, restaurants, restaurantOrders);
    }

    @Test
    void customerGetsOnlyTheirOwnOrdersWithStatus() {
        CustomerOrderStatusView view = dashboardService.customerDashboard("7", UserRole.CUSTOMER);

        assertEquals(2, view.orders().size());
        assertEquals("101", view.orders().get(0).id());
        assertEquals("placed", view.orders().get(0).status());
        assertEquals("Cafe", view.orders().get(0).restaurantName());
        assertEquals(9.00, view.orders().get(0).total());
        assertEquals("Wrap", view.orders().get(0).lines().get(0).name());
        assertEquals("rejected", view.orders().get(1).status());
        assertEquals("Sold out", view.orders().get(1).rejectReason());

        // Only customer 7's orders were ever asked for.
        verify(orders).findByCustomerId("7");
    }

    @Test
    void customerWithoutOrdersGetsAnEmptyList() {
        when(orders.findByCustomerId("71")).thenReturn(List.of());

        assertTrue(dashboardService.customerDashboard("71", UserRole.CUSTOMER).orders().isEmpty());
    }

    @Test
    void staffGetTheirOwnRestaurantAndQueue() {
        PreparationQueueView view = dashboardService.restaurantDashboard("8", UserRole.RESTAURANT);

        assertEquals("1", view.restaurantId());
        assertEquals("Cafe", view.restaurantName());
        assertEquals("open", view.restaurantStatus());
        assertEquals(2, view.queue().size());
        assertEquals(1, view.queue().get(0).getPosition());
        assertEquals("102", view.queue().get(0).getOrderId());
    }

    @Test
    void staffWithoutARestaurantIsRefused() {
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> dashboardService.restaurantDashboard("9", UserRole.RESTAURANT));

        verify(restaurantOrders, never()).getPreparationQueue(anyString(), any(), anyString());
    }

    @Test
    void eachDashboardRefusesOtherRoles() {
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> dashboardService.customerDashboard("8", UserRole.RESTAURANT));
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> dashboardService.customerDashboard("5", UserRole.COURIER));
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> dashboardService.restaurantDashboard("7", UserRole.CUSTOMER));
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> dashboardService.restaurantDashboard("6", UserRole.OPERATOR));

        verify(orders, never()).findByCustomerId(anyString());
        verify(restaurantOrders, never()).getPreparationQueue(anyString(), any(), anyString());
    }

    private void assertRefused(DomainRuleViolation.Reason reason, Runnable read) {
        DomainRuleViolation e = assertThrows(DomainRuleViolation.class, read::run);
        assertEquals(reason, e.getReason());
    }

    private static Order order(String id, OrderStatus status, String rejectReason) {
        return new Order(id, "7", "1", null, status, "No onions", null, null, null, rejectReason,
                List.of(OrderLine.fromMenuItem(WRAP, 2)));
    }
}
