package com.fortunategoods.application.dashboard;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.fortunategoods.application.ord.orders.RestaurantOrderService;
import com.fortunategoods.application.ord.orders.preparation.GetPreparationQueueResponse;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.order.validation.OrderVerify;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;

@Service
public class RestaurantDashboardQueryService {

    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantOrderService restaurantOrderService; // already builds the queue for one restaurant

    public RestaurantDashboardQueryService(UserRepository userRepository, RestaurantRepository restaurantRepository,
            RestaurantOrderService restaurantOrderService) {
        this.userRepository = userRepository;
        this.restaurantRepository = restaurantRepository;
        this.restaurantOrderService = restaurantOrderService;
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
}