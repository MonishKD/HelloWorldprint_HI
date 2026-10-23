package com.fortunategoods.application.dashboard;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.UserRepository;

/*
    The restaurant's orders (#132, R-ORD-03) that the restaurant dashboard organizes into new, preparation queue
    and ready, oldest first, for the signed-in staff's own restaurant only. The queue order itself comes from
    GET /api/restaurants/{id}/queue; this gives the dashboard each order's details through GET /api/dashboard/orders.
    (The A2 PreparationQueueView itself is the /restaurant read, #135.)
    Reading is not a command (like the console feed), so it is not logged.
*/
@Service
public class RestaurantOrderList {
    private final OrderRepository orders;
    private final UserRepository users;

    public RestaurantOrderList(OrderRepository orders, UserRepository users) {
        this.orders = orders;
        this.users = users;
    }

    public List<DashboardOrder> ordersOf(Session session) {
        String restaurantId = users.findRestaurantIdByUserId(String.valueOf(session.getUserID()));
        if (restaurantId == null) {
            return List.of();
        }
        return orders.findByRestaurant(restaurantId).stream().map(DashboardOrder::of).toList();
    }
}
