package com.fortunategoods.application.dashboard;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderEvent;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.technical.persistence.OrderEventRepository;
import com.fortunategoods.technical.persistence.UserRepository;

// Reads what the customer dashboard shows about a delivery. The order has a courier once one accepts it.
@Service
public class OrderDeliveryReader {
    private final UserRepository users;
    private final OrderEventRepository events;

    public OrderDeliveryReader(UserRepository users, OrderEventRepository events) {
        this.users = users;
        this.events = events;
    }

    public OrderDelivery of(Order order) {
        if (order.getCourierId() == null) {
            return new OrderDelivery(null, null, null, order.getDeliveryLatitude(), order.getDeliveryLongitude());
        }
        LocalDateTime pickedUpAt = null;
        LocalDateTime deliveredAt = null;
        for (OrderEvent event : events.findByOrderId(order.getId())) {
            if (event.getNewStatus() == OrderStatus.PICKED_UP) {
                pickedUpAt = event.getTimestamp();
            } else if (event.getNewStatus() == OrderStatus.DELIVERED) {
                deliveredAt = event.getTimestamp();
            }
        }
        return new OrderDelivery(users.findCourierName(order.getCourierId()), pickedUpAt, deliveredAt,
                order.getDeliveryLatitude(), order.getDeliveryLongitude());
    }
}
