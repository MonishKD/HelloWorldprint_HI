package com.fortunategoods.application.dashboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.validation.OrderVerify;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;

@Service
public class CustomerDashboardQueryService {

    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final OrderDeliveryReader orderDeliveryReader;

    public CustomerDashboardQueryService(OrderRepository orderRepository, RestaurantRepository restaurantRepository,
            OrderDeliveryReader orderDeliveryReader) {
        this.orderRepository = orderRepository;
        this.restaurantRepository = restaurantRepository;
        this.orderDeliveryReader = orderDeliveryReader;
    }

    // Throws FORBIDDEN for any other role. No orders yet gives an empty list.
    public CustomerOrderStatusView customerDashboard(String userId, UserRole role) {
        OrdModulePermissions.requireRole(role, OrdModulePermissions.viewOwnOrdersRoles(), "Only customers have an order status dashboard");
        OrderVerify.requireId(userId, "Customer ID");

        List<Order> orders = orderRepository.findByCustomerId(userId);
        // Each restaurant is looked up once, however many orders come from it.
        Map<String, String> restaurantNames = new HashMap<>();
        Map<String, OrderDelivery> deliveries = new HashMap<>();
        for (Order order : orders) {
            restaurantNames.computeIfAbsent(order.getRestaurantId(), this::restaurantName);
            deliveries.put(order.getId(), orderDeliveryReader.of(order));
        }
        return CustomerOrderStatusView.of(orders, restaurantNames, deliveries);
    }

    // null when the restaurant no longer exists.
    private String restaurantName(String restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId);
        return restaurant == null ? null : restaurant.getName();
    }
}