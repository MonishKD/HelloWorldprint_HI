package com.fortunategoods.application.dashboard;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

import com.fortunategoods.application.ord.OrderExceptionHandler;
import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.orders.RestaurantOrderService;
import com.fortunategoods.application.ord.orders.preparation.GetPreparationQueueResponse;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;
import com.fortunategoods.technical.security.SecurityConfig;
import com.fortunategoods.technical.security.SecurityErrorResponses;
import com.fortunategoods.technical.security.role.RoleCheck;
import com.fortunategoods.technical.security.session.LoggedInUser;
import com.fortunategoods.technical.security.session.SpringSecuritySessionValidator;

// The dashboard routes, through the real security, CommandInvoker and DashboardService. Only the repositories are mocked.
@WebMvcTest(
    controllers = DashboardController.class,
    properties = {
        "app.cors.allowed-origin=http://localhost:5173",
        "spring.application.name=fortunategoods-test"
    }
)
@Import({
    SecurityConfig.class, SecurityErrorResponses.class,
    SpringSecuritySessionValidator.class, RoleCheck.class,
    CommandInvoker.class, OrderExceptionHandler.class,
    DashboardService.class, OutputConsoleFeed.class
})
class DashboardControllerTest {

    private static final MenuItem WRAP = new MenuItem("10", "1", "Wrap", MenuItemStatus.AVAILABLE, 4.50, 9.00);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderRepository orders;

    @MockitoBean
    private UserRepository users;

    @MockitoBean
    private RestaurantRepository restaurants;

    @MockitoBean
    private RestaurantOrderService restaurantOrders;

    @MockitoBean
    private CommandLogRepository logs;

    @BeforeEach
    void setup() {
        when(orders.findByCustomerId("7")).thenReturn(List.of(order("101", OrderStatus.PLACED, null), order("100", OrderStatus.REJECTED, "Sold out")));
        when(orders.findByCustomerId("71")).thenReturn(List.of());
        when(users.findRestaurantIdByUserId("8")).thenReturn("1");
        when(restaurants.findById("1")).thenReturn(new Restaurant("1", "Cafe", RestaurantStatus.OPEN, 45.5, -73.6, List.of()));
        when(restaurantOrders.getPreparationQueue("8", UserRole.RESTAURANT, "1")).thenReturn(List.of(
                new GetPreparationQueueResponse(1, "102", OrderStatus.ACCEPTED),
                new GetPreparationQueueResponse(2, "103", OrderStatus.ACCEPTED)));
    }

    @Test
    void customerGetsTheirOrders() throws Exception {
        mvc.perform(get("/api/dashboard/customer").with(loggedIn(7, UserRole.CUSTOMER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orders.length()").value(2))
            .andExpect(jsonPath("$.data.orders[0].id").value("101"))
            .andExpect(jsonPath("$.data.orders[0].status").value("placed"))
            .andExpect(jsonPath("$.data.orders[0].restaurantName").value("Cafe"))
            .andExpect(jsonPath("$.data.orders[0].total").value(9.0))
            .andExpect(jsonPath("$.data.orders[0].lines[0].name").value("Wrap"))
            .andExpect(jsonPath("$.data.orders[1].rejectReason").value("Sold out"));
    }

    @Test
    void customerWithoutOrdersGetsAnEmptyView() throws Exception {
        mvc.perform(get("/api/dashboard/customer").with(loggedIn(71, UserRole.CUSTOMER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orders").isEmpty());
    }

    @Test
    void staffGetTheirRestaurantAndQueue() throws Exception {
        mvc.perform(get("/api/dashboard/restaurant").with(loggedIn(8, UserRole.RESTAURANT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.restaurantId").value("1"))
            .andExpect(jsonPath("$.data.restaurantName").value("Cafe"))
            .andExpect(jsonPath("$.data.restaurantStatus").value("open"))
            .andExpect(jsonPath("$.data.queue[0].position").value(1))
            .andExpect(jsonPath("$.data.queue[0].orderId").value("102"))
            .andExpect(jsonPath("$.data.queue[0].status").value("accepted"));
    }

    @Test
    void guestsAreRefused() throws Exception {
        mvc.perform(get("/api/dashboard/customer"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/dashboard/restaurant"))
            .andExpect(status().isUnauthorized());

        verify(orders, never()).findByCustomerId(anyString());
    }

    @Test
    void crossRoleRequestsAreRefused() throws Exception {
        mvc.perform(get("/api/dashboard/restaurant").with(loggedIn(7, UserRole.CUSTOMER)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/dashboard/customer").with(loggedIn(8, UserRole.RESTAURANT)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard/customer").with(loggedIn(5, UserRole.COURIER)))
            .andExpect(status().isForbidden());

        verify(orders, never()).findByCustomerId(anyString());
    }

    @Test
    void staffWithoutARestaurantIsRefused() throws Exception {
        mvc.perform(get("/api/dashboard/restaurant").with(loggedIn(9, UserRole.RESTAURANT)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("Your account is not linked to a restaurant"));
    }

    private RequestPostProcessor loggedIn(long userId, UserRole role) {
        var login = UsernamePasswordAuthenticationToken.authenticated(new LoggedInUser(userId, role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(login);
    }

    private static Order order(String id, OrderStatus status, String rejectReason) {
        return new Order(id, "7", "1", null, status, "No onions", null, null, null, rejectReason,
                List.of(OrderLine.fromMenuItem(WRAP, 2)));
    }
}
