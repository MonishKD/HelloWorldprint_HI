package com.fortunategoods.application.dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fortunategoods.domain.courier.Assignment;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.validation.OrderVerify;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.AssignmentRepository;
import com.fortunategoods.technical.persistence.LedgerRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;

@Service
public class OperatorOrdersQueryService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final AssignmentRepository assignmentRepository; // which courier has an order right now
    private final LedgerRepository ledgerRepository;

    public OperatorOrdersQueryService(OrderRepository orderRepository, UserRepository userRepository,
            RestaurantRepository restaurantRepository, AssignmentRepository assignmentRepository,
            LedgerRepository ledgerRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.restaurantRepository = restaurantRepository;
        this.assignmentRepository = assignmentRepository;
        this.ledgerRepository = ledgerRepository;
    }

    // #274: every order, oldest first, with its current state, restaurant and courier. Throws FORBIDDEN for any
    // role but the operator. No orders gives an empty list.
    public List<OperatorOrderResponse> allOrders(String userId, UserRole role) {
        OrdModulePermissions.requireRole(role, OrdModulePermissions.viewAllOrdersRoles(), "Only the operator can view all orders");
        OrderVerify.requireId(userId, "Operator ID");

        Map<String, Long> chargedCents = ledgerRepository.findChargedAmounts();
        List<OperatorOrderResponse> responses = new ArrayList<>();
        for (Order order : orderRepository.findAll()) {
            // The courier comes from the order's active assignment (offered or accepted, not delivered yet)
            Assignment active = assignmentRepository.findActiveByOrder(order.getId());
            String courierId = active == null ? null : active.getCourierId();
            String courierName = courierId == null ? null : userRepository.findCourierName(courierId);
            Long charged = chargedCents.get(order.getId());
            responses.add(new OperatorOrderResponse(DashboardOrder.of(order), restaurantName(order.getRestaurantId()),
                    courierId, courierName, charged == null ? null : charged / 100.0));
        }
        return List.copyOf(responses);
    }

    // null when the restaurant no longer exists.
    private String restaurantName(String restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId);
        return restaurant == null ? null : restaurant.getName();
    }
}