package com.fortunategoods.application.ord.orders;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fortunategoods.application.ord.orders.preparation.GetPreparationQueueResponse;
import com.fortunategoods.application.ord.orders.preparation.RegisterPreparationQueue;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.order.PreparationQueue;
import com.fortunategoods.domain.order.validation.OrderVerify;
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
    private final RegisterPreparationQueue pq;

    public RestaurantOrderService(UserRepository users, OrderRepository orders, RegisterPreparationQueue pq) {
        this.users = users;
        this.orders = orders;
        this.pq = pq;
    }

    public List<Order> getPendingOrders(String staffId, UserRole role, String restaurantId) {
        // Only restaurant staff may use this operation.
        OrdModulePermissions.requireRole(role, OrdModulePermissions.viewPlacedOrdersRoles(), "Only restaurant staff can view pending orders");

        OrderVerify.requireId(staffId, "Staff ID");
        OrderVerify.requireId(restaurantId, "Restaurant ID");

        // Find the restaurant associated with this staff account.
        String ownRestaurantId = users.findRestaurantIdByUserId(staffId);

        // If RestaurantId of the invoker is not the same as the one found under her name in the UserRepo, throw FORBIDDEN
        OrdModulePermissions.requireOwnRestaurant(ownRestaurantId, restaurantId, "You can only view your own restaurant's orders");

        // The repository already filters and orders the results.
        List<Order> pendingOrders = orders.findByRestaurantAndStatus(ownRestaurantId, OrderStatus.PLACED);

        // Return pending orders
        return List.copyOf(pendingOrders);
    }

    public List<GetPreparationQueueResponse> getPreparationQueue(String staffId, UserRole role, String restaurantId) {

        OrdModulePermissions.requireRole(role, OrdModulePermissions.viewPreparationQueueRoles(), "Only restaurant staff can view the preparation queue");
        // Verify ID validity
        OrderVerify.requireId(staffId, "Staff ID");
        OrderVerify.requireId(restaurantId, "Restaurant ID");
        // Find staff's restaurant
        String ownRestaurantId = users.findRestaurantIdByUserId(staffId);
        // Compare PQ's restaurant to staff's restaurant
        OrdModulePermissions.requireOwnRestaurant(ownRestaurantId, restaurantId, "You can only view your own restaurant's queue");
        // Create a PQ with all ACCEPTED orders from that restaurant
        PreparationQueue queue = pq.forRestaurant(ownRestaurantId);
        // Initialize an array to hold a response object for the PQs
        List<GetPreparationQueueResponse> result = new ArrayList<>();

        // Go through each entries and create a response object and add it to the ArrayList result
        for (PreparationQueue.QueueSlot slot : queue.snapshot()) {
            result.add(new GetPreparationQueueResponse(slot.position(), slot.orderId(), slot.status()));
        }
        // Return the list of entries in the PQ as an arrayList of those responses
        return List.copyOf(result);
    }
}

