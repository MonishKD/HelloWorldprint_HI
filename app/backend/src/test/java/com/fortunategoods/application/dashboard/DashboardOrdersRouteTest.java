package com.fortunategoods.application.dashboard;

import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
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
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.security.SecurityConfig;
import com.fortunategoods.technical.security.SecurityErrorResponses;
import com.fortunategoods.technical.security.role.RoleCheck;
import com.fortunategoods.technical.security.session.LoggedInUser;
import com.fortunategoods.technical.security.session.SpringSecuritySessionValidator;

// GET /api/dashboard/orders (#132, #135): the role decides which view answers, and the session decides whose orders.
@WebMvcTest(
    controllers = DashboardController.class,
    properties = {
        "app.cors.allowed-origin=http://localhost:5173",
        "spring.application.name=fortunategoods-test"
    }
)
@Import({
    SecurityConfig.class, SecurityErrorResponses.class,
    SpringSecuritySessionValidator.class, RoleCheck.class
})
public class DashboardOrdersRouteTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OutputConsoleFeed outputConsoleFeed;

    @MockitoBean
    private CustomerOrderList customerOrderList;

    @MockitoBean
    private RestaurantOrderList restaurantOrderList;

    // Used by the /customer and /restaurant routes, which DashboardControllerTest covers.
    @MockitoBean
    private CommandInvoker invoker;

    @MockitoBean
    private DashboardService dashboardService;

    @Test
    void customerGetsTheirOwnOrders() throws Exception {
        when(customerOrderList.ordersOf(new Session(8, UserRole.CUSTOMER))).thenReturn(List.of(order("100", "placed")));

        mvc.perform(get("/api/dashboard/orders").with(loggedIn(UserRole.CUSTOMER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].orderId").value("100"))
        .andExpect(jsonPath("$[0].status").value("placed"))
        .andExpect(jsonPath("$[0].lines[0].price").value(3.0))
        .andExpect(jsonPath("$[0].lines[0].originalPrice").value(7.0))
        .andExpect(jsonPath("$[0].total").value(6.0));

        verifyNoInteractions(restaurantOrderList);
    }

    @Test
    void restaurantStaffGetTheirRestaurantsOrders() throws Exception {
        when(restaurantOrderList.ordersOf(new Session(8, UserRole.RESTAURANT))).thenReturn(List.of(order("100", "accepted"), order("101", "placed")));

        mvc.perform(get("/api/dashboard/orders").with(loggedIn(UserRole.RESTAURANT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].status").value("accepted"));

        verifyNoInteractions(customerOrderList);
    }

    @Test
    void courierAndOperatorAreRefused() throws Exception {
        for (UserRole role : List.of(UserRole.COURIER, UserRole.OPERATOR)) {
            mvc.perform(get("/api/dashboard/orders").with(loggedIn(role)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        verifyNoInteractions(customerOrderList, restaurantOrderList);
    }

    @Test
    void missingSessionIsRefused() throws Exception {
        mvc.perform(get("/api/dashboard/orders"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(customerOrderList, restaurantOrderList);
    }

    private DashboardOrder order(String id, String status) {
        return new DashboardOrder(id, "1", "7", status, "No onions", "1 Main St", null,
                List.of(new DashboardOrder.Line("10", "Quiche", 3.0, 7.0, 2)), 6.0, null, null, null, null, null);
    }

    private RequestPostProcessor loggedIn(UserRole role) {
        var login = UsernamePasswordAuthenticationToken.authenticated(new LoggedInUser(8, role), null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(login);
    }
}
