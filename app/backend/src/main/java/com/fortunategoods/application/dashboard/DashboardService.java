package com.fortunategoods.application.dashboard;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fortunategoods.domain.user.UserRole;

/*
    Facade over the dashboard query services, kept so existing callers do not change.
    Each role only gets its own data: the customer and the staff member's restaurant come from the session,
    never from the request.
*/
@Service
public class DashboardService {

    private final CustomerDashboardQueryService customerDashboardQueryService;
    private final RestaurantDashboardQueryService restaurantDashboardQueryService;
    private final OperatorOrdersQueryService operatorOrdersQueryService;
    private final CityViewQueryService cityViewQueryService;

    public DashboardService(CustomerDashboardQueryService customerDashboardQueryService,
            RestaurantDashboardQueryService restaurantDashboardQueryService,
            OperatorOrdersQueryService operatorOrdersQueryService, CityViewQueryService cityViewQueryService) {
        this.customerDashboardQueryService = customerDashboardQueryService;
        this.restaurantDashboardQueryService = restaurantDashboardQueryService;
        this.operatorOrdersQueryService = operatorOrdersQueryService;
        this.cityViewQueryService = cityViewQueryService;
    }

    public CustomerOrderStatusView customerDashboard(String userId, UserRole role) {
        return customerDashboardQueryService.customerDashboard(userId, role);
    }

    public PreparationQueueView restaurantDashboard(String userId, UserRole role) {
        return restaurantDashboardQueryService.restaurantDashboard(userId, role);
    }

    public List<OperatorOrderResponse> allOrders(String userId, UserRole role) {
        return operatorOrdersQueryService.allOrders(userId, role);
    }

    public CityView cityView(UserRole role) {
        return cityViewQueryService.cityView(role);
    }
}