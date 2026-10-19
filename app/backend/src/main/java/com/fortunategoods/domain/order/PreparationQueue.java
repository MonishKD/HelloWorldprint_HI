package com.fortunategoods.domain.order; //package before imports!

import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Comparator;
import java.time.LocalDateTime;

public class PreparationQueue {
    private final String RestaurantID;

    private record Entry(Order order, LocalDateTime timestamp) 
    {
    }

    private final Queue<Entry> queue;


    public PreparationQueue(String restaurantId) {
        if (restaurantId == null || restaurantId.trim().isEmpty()) {
            throw new IllegalArgumentException("Given restaurant ID to PreparationQueue constructor is null or empty! Fix this!");
        }
        this.RestaurantID = restaurantId;
        queue = new PriorityQueue<>(Comparator.comparing(Entry::timestamp));
    }

    //returns null when empty
    public synchronized Order peek() {
        if (queue.isEmpty()) {
            return null;
        }
        return queue.peek().order();
    }

    //returns null when empty
    public synchronized Order dequeue() {
        if (queue.isEmpty()) {
            return null;
        }
        return queue.remove().order();
    }

    public synchronized Order remove(Order order) {
        return queue.removeIf(entry -> entry.order().equals(order)) ? order : null;
    }

    public synchronized void enqueue(Order order, LocalDateTime timestamp) {
        if (order == null) {
            throw new IllegalArgumentException("Given order to queue is null!");
        }

        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp added to the queue is null!");
        }

        if (!order.getRestaurantId().equals(RestaurantID)) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Order does not belong to this current queue's restaurant");
        }
        queue.add(new Entry(order, timestamp));
    }

}
