package com.fortunategoods.application.dashboard;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.UserRepository;

/*
    A2 PreparationQueueView (#132, R-ORD-03): the orders the restaurant dashboard organizes into new, preparation queue
    and ready, oldest first, for the signed-in staff's own restaurant only. The queue order itself comes from
    GET /api/restaurants/{id}/queue; this gives the dashboard each order's details.
    Reading is not a command (like the console feed), so it is not logged.
*/
@Service
public class PreparationQueueView {
    private final OrderRepository orders;
    private final UserRepository users;

    public PreparationQueueView(OrderRepository orders, UserRepository users) {
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
