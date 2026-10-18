package com.fortunategoods.application.ord;
import com.fortunategoods.application.ord.orders.pending.RestaurantOrderService;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.UserRepository;

public class RestaurantOrderServiceTest {

    private UserRepository users;
    private OrderRepository orders;
    private RestaurantOrderService service;

    @BeforeEach
    void setup() {
        users = mock(UserRepository.class);
        orders = mock(OrderRepository.class);

        service = new RestaurantOrderService(users, orders);

        // Staff member 8 belongs to restaurant 1.
        when(users.findRestaurantIdByUserId("8")).thenReturn("1");
    }

    @Test
    void staffCanViewOwnPendingOrders() {
        when(orders.findByRestaurantAndStatus("1", OrderStatus.PLACED)).thenReturn(List.of(createOrder("100"), createOrder("101")));

        List<Order> result = service.getPendingOrders("8", UserRole.RESTAURANT, "1");

        assertEquals(2, result.size());

        // Preserve the repository's oldest-first order.
        assertEquals("100", result.get(0).getId());
        assertEquals("101", result.get(1).getId());

        Order first = result.get(0);

        assertEquals("1", first.getRestaurantId());
        assertEquals(OrderStatus.PLACED, first.getStatus());
        assertEquals("No onions", first.getSpecialInstructions());
        assertEquals(1, first.getLines().size());
        assertEquals("Wrap", first.getLines().get(0).getName());
        assertEquals(2, first.getLines().get(0).getQuantity());
        verify(orders).findByRestaurantAndStatus("1", OrderStatus.PLACED);
    }

    @Test
    void noPendingOrdersReturnsEmptyList() {
        when(orders.findByRestaurantAndStatus("1", OrderStatus.PLACED)).thenReturn(List.of());

        List<Order> result = service.getPendingOrders("8", UserRole.RESTAURANT, "1");
        assertTrue(result.isEmpty());
    }

    @Test
    void wrongRestaurantOrMissingAssociationIsForbidden() {
        
        String[] associations = {"2", null};

        for (String association : associations) {
            when(users.findRestaurantIdByUserId("8")).thenReturn(association);

            DomainRuleViolation error = assertThrows(DomainRuleViolation.class, () -> service.getPendingOrders("8", UserRole.RESTAURANT, "1"));

            assertEquals(DomainRuleViolation.Reason.FORBIDDEN,error.getReason());
        }
        verifyNoInteractions(orders);
    }

    @Test
    void otherRolesAreForbidden() {
        UserRole[] roles = {
            UserRole.CUSTOMER,
            UserRole.COURIER,
            UserRole.OPERATOR
        };

        for (UserRole role : roles) {
            DomainRuleViolation error = assertThrows(DomainRuleViolation.class, () -> service.getPendingOrders("8", role, "1"));

            assertEquals(DomainRuleViolation.Reason.FORBIDDEN, error.getReason());
        }

        verifyNoInteractions(users, orders);
    }

    @Test
    void invalidIdsAreRejected() {
        String[] invalidIds = {null, "", "abc", "0"};

        for (String id : invalidIds) {
            assertThrows(DomainRuleViolation.class, () -> service.getPendingOrders(id, UserRole.RESTAURANT, "1"));

            assertThrows(DomainRuleViolation.class, () -> service.getPendingOrders("8", UserRole.RESTAURANT, id));
        }
        verifyNoInteractions(users, orders);
    }

    private Order createOrder(String orderId) {
        MenuItem item = new MenuItem("10", "1", "Wrap", MenuItemStatus.AVAILABLE, 4.50);

        return new Order(orderId, "7", "1", null, OrderStatus.PLACED, "No onions", null, null, null, null, List.of(OrderLine.fromMenuItem(item, 2)));
    }
}