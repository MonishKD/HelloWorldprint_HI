package com.fortunategoods.domain.order; //package before imports!

import java.util.PriorityQueue;
import java.util.Queue;
import java.util.ArrayList;
import java.util.Comparator;
import java.time.LocalDateTime;
import java.util.List;

public class PreparationQueue {
    private final String RestaurantID;

    private record Entry(Order order, LocalDateTime timestamp, long seq){}

    // First in, first out: the oldest timestamp goes first. If two have the same timestamp, the lower seq goes first.
    private static class FifoOrder implements Comparator<Entry> {
        @Override
        public int compare(Entry first, Entry second) {
            int byTime = first.timestamp().compareTo(second.timestamp());
            if (byTime != 0) {
                return byTime;
            }
            return Long.compare(first.seq(), second.seq());
        }
    }

    private static final Comparator<Entry> FIFO = new FifoOrder();

    // Queue for the restaurant will be initialized as soon as it is created for that Restaurant
    private final Queue<Entry> queue = new PriorityQueue<>(FIFO);


    public PreparationQueue(String restaurantId) {
        if (restaurantId == null || restaurantId.trim().isEmpty()) {
            throw new IllegalArgumentException("Given restaurant ID to PreparationQueue constructor is null or empty! Fix this!");
        }
        this.RestaurantID = restaurantId;
    }

    //returns null when empty
    public synchronized Order peek() {
        Entry e = queue.peek();
        return e == null ? null: e.order();
    }

    //returns null when empty
    public synchronized Order dequeue() {
        Entry e = queue.poll(); // won't throw an exception
        return e == null? null: e.order();
    }

    public synchronized Order remove(Order order) {
        if (order == null || order.getId() == null) {
            return null;
        }
        // Find first entry specified in the method, or else return null if not found
        Entry found = queue.stream().filter(entry -> order.getId().equals(entry.order().getId())).findFirst().orElse(null);

        if (found == null) {
            return null;
        }
        queue.remove(found);
        return found.order();
    }

    public synchronized void enqueue(Order order, LocalDateTime timestamp, long acceptedEventId) {
        if (order == null || order.getId() == null || order.getId().isBlank()) {
            throw new IllegalArgumentException("Given order to queue is null!");
        }

        if (timestamp == null || acceptedEventId <= 0) {
            throw new IllegalArgumentException("Timestamp added to the queue is null!");
        }

        if (!order.getRestaurantId().equals(RestaurantID)) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Order does not belong to this current queue's restaurant");
        }

        // Check if order is in the ACCEPTED state
        if (order.getStatus() != OrderStatus.ACCEPTED){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Only ACCEPTED orders can be placed in the preparation queue");
        }

        // Check if order is already in queue
        boolean alreadyInQueue = queue.stream().anyMatch(entry -> order.getId().equals(entry.order().getId()));

        if (!alreadyInQueue){
            queue.add(new Entry(order, timestamp, acceptedEventId));
        }

    }

    public record QueueSlot(int position, String orderId, OrderStatus status) {}

    public synchronized List<QueueSlot> snapshot() {
        List<Entry> sorted = queue.stream().sorted(FIFO).toList();
        List<QueueSlot> slots = new ArrayList<>(); // removed size ArrayLists grow

        for (int i = 0; i < sorted.size(); i++) {
            Order o = sorted.get(i).order();
            slots.add(new QueueSlot(i + 1, o.getId(), o.getStatus()));
        }
        return List.copyOf(slots);
    }

}
