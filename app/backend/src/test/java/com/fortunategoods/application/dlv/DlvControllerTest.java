package com.fortunategoods.application.dlv;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fortunategoods.domain.courier.DispatchQueue.WaitingOrder;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.AssignmentRepository;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.technical.persistence.CourierRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.testsupport.SecuredControllerTest;
import static com.fortunategoods.testsupport.TestData.loggedIn;
import static com.fortunategoods.testsupport.TestData.order;

@SecuredControllerTest(DlvController.class)
@Import(DispatchService.class)
class DlvControllerTest {
    private static final String URL = "/api/operator/dispatch-queue";

    @Autowired private MockMvc mvc;
    @MockitoBean private ReassignmentService reassignmentService;
    @MockitoBean private DeliveryService deliveryService;
    @MockitoBean private OrderRepository orders;
    @MockitoBean private RestaurantRepository restaurants;
    @MockitoBean private CourierRepository couriers;
    @MockitoBean private AssignmentRepository assignments;
    @MockitoBean private CommandLogRepository logs;

    @Test
    void operatorGetsTheQueue() throws Exception {
        when(restaurants.findById("1")).thenReturn(new Restaurant("1", "Cafe", RestaurantStatus.OPEN, 45.5, -73.6, List.of()));
        when(orders.findWaitingForDispatch()).thenReturn(List.of(
            new WaitingOrder(order("100", OrderStatus.READY), LocalDateTime.of(2026, 11, 5, 12, 0), 701)));

        mvc.perform(get(URL).with(loggedIn(3, UserRole.OPERATOR)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].orderId").value("100"))
            .andExpect(jsonPath("$.data[0].restaurantName").value("Cafe"))
            .andExpect(jsonPath("$.data[0].waitingSince").value("2026-11-05T12:00:00"));
    }

    @Test
    void otherRolesAreRefused() throws Exception {
        mvc.perform(get(URL).with(loggedIn(8, UserRole.RESTAURANT))).andExpect(status().isForbidden());
        verify(orders, never()).findWaitingForDispatch();
    }
}
