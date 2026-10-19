package com.fortunategoods.application.ord.orders.preparation;


import java.util.List;

import org.springframework.stereotype.Component;

import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderEvent;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.order.PreparationQueue;
import com.fortunategoods.domain.order.validation.OrderVerify;
import com.fortunategoods.technical.persistence.OrderEventRepository;
import com.fortunategoods.technical.persistence.OrderRepository;

@Component 
public class RegisterPreparationQueue {
    private final OrderRepository orders;
    private final OrderEventRepository events;

    public RegisterPreparationQueue(OrderRepository orders, OrderEventRepository events) {
        this.orders = orders;
        this.events = events;
    }

    public PreparationQueue forRestaurant(String restaurantId){
        OrderVerify.requireId(restaurantId, "Restaurant ID"); // Verify validity of ID

        // Create a PQ for the restaurant
        PreparationQueue queue = new PreparationQueue(restaurantId);
        // Find the list of ACCEPTED orders from the Order repo
        List<Order> acceptedOrders = orders.findByRestaurantAndStatus(restaurantId, OrderStatus.ACCEPTED);

        for (Order order : acceptedOrders) {
            // Repository returns events oldest first.
            OrderEvent acceptedEvent = events.findByOrderId(order.getId()).stream()
            .filter(event -> event.getNewStatus() == OrderStatus.ACCEPTED)
            .reduce((earlier, later) -> later)
            .orElseThrow(() -> new IllegalStateException("Accepted order has no acceptance event"));

            queue.enqueue(order, acceptedEvent.getTimestamp(), Long.parseLong(acceptedEvent.getId()));
        }

        return queue;
    }
}
