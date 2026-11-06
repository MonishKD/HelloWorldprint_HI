package com.fortunategoods.application.dashboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fortunategoods.application.ord.orders.RestaurantOrderService;
import com.fortunategoods.application.ord.orders.preparation.GetPreparationQueueResponse;
import com.fortunategoods.domain.courier.Assignment;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.order.validation.OrderVerify;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.AssignmentRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;

/*
    Receiver of the dashboard commands. Each role only gets its own data:
    the customer and the staff member's restaurant come from the session, never from the request.
*/
@Service
public class DashboardService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantOrderService restaurantOrderService; // already builds the queue for one restaurant
    private final AssignmentRepository assignmentRepository; // which courier has an order right now

    public DashboardService(OrderRepository orderRepository, UserRepository userRepository,
            RestaurantRepository restaurantRepository, RestaurantOrderService restaurantOrderService,
            AssignmentRepository assignmentRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.restaurantRepository = restaurantRepository;
        this.restaurantOrderService = restaurantOrderService;
        this.assignmentRepository = assignmentRepository;
    }

    // Throws FORBIDDEN for any other role. No orders yet gives an empty list.
    public CustomerOrderStatusView customerDashboard(String userId, UserRole role) {
        OrdModulePermissions.requireRole(role, OrdModulePermissions.viewOwnOrdersRoles(), "Only customers have an order status dashboard");
        OrderVerify.requireId(userId, "Customer ID");

        List<Order> orders = orderRepository.findByCustomerId(userId);
        // Each restaurant is looked up once, however many orders come from it.
        Map<String, String> restaurantNames = new HashMap<>();
        for (Order order : orders) {
            restaurantNames.computeIfAbsent(order.getRestaurantId(), this::restaurantName);
        }
        return CustomerOrderStatusView.of(orders, restaurantNames);
    }

    // Throws FORBIDDEN for any other role or a staff account with no restaurant. An empty queue gives an empty list.
    public PreparationQueueView restaurantDashboard(String userId, UserRole role) {
        OrdModulePermissions.requireRole(role, OrdModulePermissions.viewPreparationQueueRoles(), "Only restaurant staff have a restaurant dashboard");
        OrderVerify.requireId(userId, "Staff ID");

        String ownRestaurantId = userRepository.findRestaurantIdByUserId(userId);
        if (ownRestaurantId == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN, "Your account is not linked to a restaurant");
        }
        Restaurant restaurant = restaurantRepository.findById(ownRestaurantId);
        if (restaurant == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "Restaurant not found");
        }

        List<GetPreparationQueueResponse> queue = restaurantOrderService.getPreparationQueue(userId, role, ownRestaurantId);
        return new PreparationQueueView(restaurant.getId(), restaurant.getName(),
                restaurant.getStatus().name().toLowerCase(Locale.ROOT), queue);
    }

    // #274: every order, oldest first, with its current state, restaurant and courier. Throws FORBIDDEN for any
    // role but the operator. No orders gives an empty list.
    public List<OperatorOrderResponse> allOrders(String userId, UserRole role) {
        OrdModulePermissions.requireRole(role, OrdModulePermissions.viewAllOrdersRoles(), "Only the operator can view all orders");
        OrderVerify.requireId(userId, "Operator ID");

        List<OperatorOrderResponse> responses = new ArrayList<>();
        for (Order order : orderRepository.findAll()) {
            // The courier comes from the order's active assignment (offered or accepted, not delivered yet)
            Assignment active = assignmentRepository.findActiveByOrder(order.getId());
            String courierId = active == null ? null : active.getCourierId();
            String courierName = courierId == null ? null : userRepository.findCourierName(courierId);
            responses.add(new OperatorOrderResponse(DashboardOrder.of(order), restaurantName(order.getRestaurantId()),
                    courierId, courierName));
        }
        return List.copyOf(responses);
    }

    // null when the restaurant no longer exists.
    private String restaurantName(String restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId);
        return restaurant == null ? null : restaurant.getName();
    }
}
