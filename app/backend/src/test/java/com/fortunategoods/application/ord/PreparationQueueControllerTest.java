package com.fortunategoods.application.ord;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fortunategoods.application.ord.orders.RestaurantOrderController;
import com.fortunategoods.application.ord.orders.RestaurantOrderService;
import com.fortunategoods.application.ord.orders.preparation.RegisterPreparationQueue;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderEvent;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.technical.persistence.OrderEventRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.UserRepository;
import com.fortunategoods.testsupport.SecuredControllerTest;
import static com.fortunategoods.testsupport.TestData.loggedIn;

// GET /api/restaurants/{id}/queue with the real RestaurantOrderService and queue. Only the repositories are mocked.
@SecuredControllerTest(RestaurantOrderController.class)
@Import({RestaurantOrderService.class, RegisterPreparationQueue.class})
class PreparationQueueControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private OrderRepository orders;
    @MockitoBean private OrderEventRepository events;
    @MockitoBean private UserRepository users;
    @MockitoBean private CommandLogRepository logs;

    @Test
    void returnsSevenOrdersInQueueOrder() throws Exception {
        when(users.findRestaurantIdByUserId("8")).thenReturn("1");
        // Supply orders backwards to check the queue sorts them.
        List<Order> accepted = new ArrayList<>();
        for (int i = 7; i >= 1; i--) {
            accepted.add(acceptedOrder(i, "1"));
        }
        when(orders.findByRestaurantAndStatus("1", OrderStatus.ACCEPTED)).thenReturn(accepted);

        ResultActions response = mvc.perform(get("/api/restaurants/1/queue").with(loggedIn(8, UserRole.RESTAURANT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(7));
        for (int i = 0; i < 7; i++) {
            response
                .andExpect(jsonPath("$.data[" + i + "].position").value(i + 1))
                .andExpect(jsonPath("$.data[" + i + "].orderId").value(String.valueOf(101 + i)))
                .andExpect(jsonPath("$.data[" + i + "].status").value("accepted"));
        }
    }

    @Test
    void restaurantsHaveSeparateQueues() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String restaurantId = String.valueOf(i);
            when(users.findRestaurantIdByUserId(String.valueOf(7 + i))).thenReturn(restaurantId);
            Order accepted = acceptedOrder(i, restaurantId);
            when(orders.findByRestaurantAndStatus(restaurantId, OrderStatus.ACCEPTED)).thenReturn(List.of(accepted));

            mvc.perform(get("/api/restaurants/" + restaurantId + "/queue").with(loggedIn(7 + i, UserRole.RESTAURANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].orderId").value(String.valueOf(100 + i)));
        }
    }

    @Test
    void emptyQueueReturnsEmptyList() throws Exception {
        when(users.findRestaurantIdByUserId("8")).thenReturn("1");
        when(orders.findByRestaurantAndStatus("1", OrderStatus.ACCEPTED)).thenReturn(List.of());

        mvc.perform(get("/api/restaurants/1/queue").with(loggedIn(8, UserRole.RESTAURANT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void cannotViewAnotherRestaurantsQueue() throws Exception {
        when(users.findRestaurantIdByUserId("8")).thenReturn("1");

        mvc.perform(get("/api/restaurants/2/queue").with(loggedIn(8, UserRole.RESTAURANT)))
            .andExpect(status().isForbidden());
        verifyNoInteractions(orders, events);
    }

    @Test
    void missingSessionAndWrongRoleAreRefused() throws Exception {
        mvc.perform(get("/api/restaurants/1/queue")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/restaurants/1/queue").with(loggedIn(8, UserRole.CUSTOMER))).andExpect(status().isForbidden());
        verifyNoInteractions(users, orders, events);
    }

    // Accepted order 100 + number of this restaurant, accepted "number" seconds after noon.
    private Order acceptedOrder(int number, String restaurantId) {
        String id = String.valueOf(100 + number);
        when(events.findByOrderId(id)).thenReturn(List.of(new OrderEvent(String.valueOf(700 + number), id, null,
                OrderStatus.PLACED, OrderStatus.ACCEPTED, LocalDateTime.of(2026, 10, 19, 12, 0).plusSeconds(number))));
        MenuItem item = new MenuItem("10", restaurantId, "Wrap", MenuItemStatus.AVAILABLE, 4.50);
        return new Order(id, "7", restaurantId, null, OrderStatus.ACCEPTED, null, null, null, null, null,
                List.of(OrderLine.fromMenuItem(item, 1)));
    }
}
