package com.fortunategoods.application.ord;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.orders.RestaurantOrderController;
import com.fortunategoods.application.ord.orders.RestaurantOrderService;
import com.fortunategoods.application.ord.orders.preparation.RegisterPreparationQueue;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderEvent;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.technical.persistence.OrderEventRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.UserRepository;
import com.fortunategoods.technical.security.SecurityConfig;
import com.fortunategoods.technical.security.SecurityErrorResponses;
import com.fortunategoods.technical.security.role.RoleCheck;
import com.fortunategoods.technical.security.session.LoggedInUser;
import com.fortunategoods.technical.security.session.SpringSecuritySessionValidator;

@WebMvcTest(
    controllers = RestaurantOrderController.class,
    properties = {
        "app.cors.allowed-origin=http://localhost:5173",
        "spring.application.name=fortunategoods-test"
    }
)
@Import({
    SecurityConfig.class, SecurityErrorResponses.class,
    SpringSecuritySessionValidator.class, RoleCheck.class,
    CommandInvoker.class, OrderExceptionHandler.class,
    RestaurantOrderService.class, RegisterPreparationQueue.class
})
class PreparationQueueControllerTest {

    @Autowired
    private MockMvc mvc;

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
            accepted.add(order("1", i));
        }
        when(orders.findByRestaurantAndStatus("1", OrderStatus.ACCEPTED))
            .thenReturn(accepted);

        var response = mvc.perform(get("/api/restaurants/1/queue")
            .with(login(8, UserRole.RESTAURANT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(7));

        for (int i = 0; i < 7; i++) {
            response
                .andExpect(jsonPath("$.data[" + i + "].position").value(i + 1))
                .andExpect(jsonPath("$.data[" + i + "].orderId")
                    .value(String.valueOf(101 + i)))
                .andExpect(jsonPath("$.data[" + i + "].status").value("accepted"));
        }
    }

    @Test
    void restaurantsHaveSeparateQueues() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String restaurantId = String.valueOf(i);
            String staffId = String.valueOf(7 + i);

            when(users.findRestaurantIdByUserId(staffId)).thenReturn(restaurantId);
            Order acceptedOrder = order(restaurantId, i);
            when(orders.findByRestaurantAndStatus(restaurantId, OrderStatus.ACCEPTED)).thenReturn(List.of(acceptedOrder));

            mvc.perform(get("/api/restaurants/" + restaurantId + "/queue").with(login(7 + i, UserRole.RESTAURANT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].orderId").value(String.valueOf(100 + i)));
        }
    }

    @Test
    void emptyQueueReturnsEmptyList() throws Exception {
        when(users.findRestaurantIdByUserId("8")).thenReturn("1");
        when(orders.findByRestaurantAndStatus("1", OrderStatus.ACCEPTED)).thenReturn(List.of());

        mvc.perform(get("/api/restaurants/1/queue").with(login(8, UserRole.RESTAURANT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void cannotViewAnotherRestaurantsQueue() throws Exception {
        when(users.findRestaurantIdByUserId("8")).thenReturn("1");

        mvc.perform(get("/api/restaurants/2/queue")
        .with(login(8, UserRole.RESTAURANT)))
        .andExpect(status().isForbidden());

        verifyNoInteractions(orders, events);
    }

    @Test
    void missingSessionAndWrongRoleAreRefused() throws Exception {
        mvc.perform(get("/api/restaurants/1/queue")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/restaurants/1/queue").with(login(8, UserRole.CUSTOMER))).andExpect(status().isForbidden());

        verifyNoInteractions(users, orders, events);
    }

    private Order order(String restaurantId, int number) {
        String id = String.valueOf(100 + number);
        Order order = mock(Order.class);

        when(order.getId()).thenReturn(id);
        when(order.getRestaurantId()).thenReturn(restaurantId);
        when(order.getStatus()).thenReturn(OrderStatus.ACCEPTED);

        when(events.findByOrderId(id)).thenReturn(List.of(
            new OrderEvent(String.valueOf(700 + number), id, null, OrderStatus.PLACED, OrderStatus.ACCEPTED, LocalDateTime.of(2026, 10, 19, 12, 0).plusSeconds(number)
            )
        ));
        return order;
    }

    private RequestPostProcessor login(long id, UserRole role) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
            new LoggedInUser(id, role), null,
            List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
        return authentication(authentication);
    }
}
