package com.fortunategoods.application.ord;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.order.OrderVerify;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.UserRepository;

/*
    Class used to verify if user invoking accept/reject isn't accesing Orders
    from another restaurant
*/
@Service
public class RestaurantOrderService {
    private final UserRepository users;
    private final OrderRepository orders;

    public RestaurantOrderService(UserRepository users, OrderRepository orders) {
        this.users = users;
        this.orders = orders;
    }

    public List<Order> getPendingOrders(String staffId, UserRole role, String restaurantId) {
        // Only restaurant staff may use this operation.
        if (role != UserRole.RESTAURANT) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN,"Only restaurant staff can view pending orders");
        }

        OrderVerify.requireId(staffId, "Staff ID");
        OrderVerify.requireId(restaurantId, "Restaurant ID");

        // Find the restaurant associated with this staff account.
        String ownRestaurantId = users.findRestaurantIdByUserId(staffId);

        // If RestaurantId of the invoker is not the same as the one found under her name in the UserRepo, throw FORBIDDEN
        if (!restaurantId.equals(ownRestaurantId)) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN, "You can only view your own restaurant's orders");
        }

        // The repository already filters and orders the results.
        List<Order> pendingOrders = orders.findByRestaurantAndStatus(ownRestaurantId, OrderStatus.PLACED);

        // Return pending orders
        return List.copyOf(pendingOrders);
    }
}

