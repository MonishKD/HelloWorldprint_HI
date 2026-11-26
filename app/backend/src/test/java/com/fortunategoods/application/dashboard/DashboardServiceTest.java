package com.fortunategoods.application.dashboard;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fortunategoods.application.ord.orders.RestaurantOrderService;
import com.fortunategoods.application.ord.orders.preparation.GetPreparationQueueResponse;
import com.fortunategoods.domain.courier.Assignment;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.*;

import static com.fortunategoods.testsupport.TestData.order;

// Characterization tests for #332. They only use DashboardService's public API and must pass before AND after
// the Extract Class refactoring. After the refactoring only the construction of DashboardService in setup() changes.
// The repositories are mocked: customer 7 has orders 101 and 100, staff 8 works for restaurant "1", staff 9 has none.
class DashboardServiceTest {

    private OrderRepository orders;
    private UserRepository users;
    private RestaurantRepository restaurants;
    private AssignmentRepository assignments;
    private RestaurantOrderService restaurantOrders;
    private LedgerRepository ledger;
    private DashboardService dashboardService;

    @BeforeEach
    void setup() {
        orders = mock(OrderRepository.class);
        users = mock(UserRepository.class);
        restaurants = mock(RestaurantRepository.class);
        assignments = mock(AssignmentRepository.class);
        restaurantOrders = mock(RestaurantOrderService.class);
        ledger = mock(LedgerRepository.class);

        when(orders.findByCustomerId("7")).thenReturn(List.of(order("101", OrderStatus.PLACED), order("100", OrderStatus.REJECTED, "Sold out")));
        when(users.findRestaurantIdByUserId("8")).thenReturn("1");
        when(restaurants.findById("1")).thenReturn(new Restaurant("1", "Cafe", RestaurantStatus.OPEN, 45.5, -73.6, List.of()));
        when(restaurantOrders.getPreparationQueue("8", UserRole.RESTAURANT, "1")).thenReturn(List.of(
                new GetPreparationQueueResponse(1, "102", OrderStatus.ACCEPTED),
                new GetPreparationQueueResponse(2, "103", OrderStatus.ACCEPTED)));

        dashboardService = new DashboardService(orders, users, restaurants, restaurantOrders, assignments, mock(CourierRepository.class),
                new OrderDeliveryReader(users, mock(OrderEventRepository.class)), ledger);
    }

    @Test
    void operatorSeesWhatAnOrderWasCharged() {
        when(orders.findAll()).thenReturn(List.of(order("100", OrderStatus.DELIVERED), order("101", OrderStatus.READY)));
        when(ledger.findChargedAmounts()).thenReturn(Map.of("100", 1269L));

        List<OperatorOrderResponse> rows = dashboardService.allOrders("6", UserRole.OPERATOR);

        assertEquals(12.69, rows.get(0).getChargedTotal());
        assertNull(rows.get(1).getChargedTotal());
    }

    @Test
    void operatorSeesTheCourierOfAnActiveAssignmentOnly() {
        Assignment active = mock(Assignment.class);
        when(active.getCourierId()).thenReturn("20");
        when(orders.findAll()).thenReturn(List.of(order("100", OrderStatus.READY), order("101", OrderStatus.READY)));
        when(assignments.findActiveByOrder("100")).thenReturn(active);
        when(users.findCourierName("20")).thenReturn("Sam");

        List<OperatorOrderResponse> rows = dashboardService.allOrders("6", UserRole.OPERATOR);

        assertEquals("20", rows.get(0).getCourierId());   // adjust getters if named differently
        assertEquals("Sam", rows.get(0).getCourierName());
        assertNull(rows.get(1).getCourierId());
        verify(users, times(1)).findCourierName(anyString());
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

        // Only customer 7's orders were ever asked for, and the restaurant was looked up once.
        verify(orders).findByCustomerId("7");
        verify(restaurants, times(1)).findById("1");
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
    void staffWhoseRestaurantNoLongerExistsGetsNotFound() {
        when(users.findRestaurantIdByUserId("10")).thenReturn("99");

        assertRefused(DomainRuleViolation.Reason.NOT_FOUND, () -> dashboardService.restaurantDashboard("10", UserRole.RESTAURANT));

        verify(restaurantOrders, never()).getPreparationQueue(anyString(), any(), anyString());
    }

    @Test
    void eachDashboardRefusesOtherRoles() {
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> dashboardService.customerDashboard("8", UserRole.RESTAURANT));
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> dashboardService.customerDashboard("5", UserRole.COURIER));
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> dashboardService.restaurantDashboard("7", UserRole.CUSTOMER));
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> dashboardService.restaurantDashboard("6", UserRole.OPERATOR));
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> dashboardService.allOrders("7", UserRole.CUSTOMER));

        verify(orders, never()).findByCustomerId(anyString());
        verify(orders, never()).findAll();
        verify(restaurantOrders, never()).getPreparationQueue(anyString(), any(), anyString());
    }

    @Test
    void theRoleIsCheckedBeforeTheId() {
        // A wrong role with a blank id must report the role problem, not the validation problem.
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> dashboardService.customerDashboard("", UserRole.COURIER));
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> dashboardService.restaurantDashboard("", UserRole.CUSTOMER));
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> dashboardService.allOrders("", UserRole.CUSTOMER));
    }

    @Test
    void cityViewListsRestaurantsAndRefusesAMissingRole() {
        when(restaurants.findAll()).thenReturn(List.of(new Restaurant("1", "Cafe", RestaurantStatus.OPEN, 45.5, -73.6, List.of())));

        CityView view = dashboardService.cityView(UserRole.CUSTOMER);

        assertTrue(view.toString().contains("Cafe"));
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> dashboardService.cityView(null));
    }

    private void assertRefused(DomainRuleViolation.Reason reason, Runnable read) {
        DomainRuleViolation e = assertThrows(DomainRuleViolation.class, read::run);
        assertEquals(reason, e.getReason());
    }
}