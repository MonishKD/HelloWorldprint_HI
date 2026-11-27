package com.fortunategoods.application.dashboard;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.rating.Rating;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RatingRepository;

/*
    The signed-in customer's own orders with their details, oldest first. Feeds GET /api/dashboard/orders,
    which the customer pages use. (The A2 CustomerOrderStatusView itself is the /customer read, #135.)
    Reading is not a command (like the console feed), so it is not logged.
*/
@Service
public class CustomerOrderList {
    private final OrderRepository orders;
    private final OrderDeliveryReader deliveries;
    private final RatingRepository ratings;

    public CustomerOrderList(OrderRepository orders, OrderDeliveryReader deliveries, RatingRepository ratings) {
        this.ratings = ratings;
        this.orders = orders;
        this.deliveries = deliveries;
    }

    public List<DashboardOrder> ordersOf(Session session) {
        return orders.findByCustomer(String.valueOf(session.getUserID())).stream()
                .map(order -> DashboardOrder.of(order, deliveries.of(order), ratingOf(order.getId()))).toList();
    }

    // null when the order is not rated.
    private DashboardOrder.Rating ratingOf(String orderId) {
        Rating rating = ratings.findByOrder(orderId);
        return rating == null ? null : new DashboardOrder.Rating(rating.restaurantStars(), rating.courierStars());
    }
}
