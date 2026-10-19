package com.fortunategoods.domain.order; //package before imports!

import java.util.PriorityQueue;
import java.util.Queue;
import java.util.ArrayList;
import java.util.Comparator;
import java.time.LocalDateTime;
import java.util.List;

public class PreparationQueue {
    private final String RestaurantID;

    private record Entry(Order order, LocalDateTime timestamp, long seq)  
    {
    }

    private static final Comparator<Entry> FIFO =
        Comparator.comparing(Entry::timestamp).thenComparingLong(Entry::seq);

    private long nextSeq = 0;

    private final Queue<Entry> queue;


    public PreparationQueue(String restaurantId) {
        if (restaurantId == null || restaurantId.trim().isEmpty()) {
            throw new IllegalArgumentException("Given restaurant ID to PreparationQueue constructor is null or empty! Fix this!");
        }
        this.RestaurantID = restaurantId;
        queue = new PriorityQueue<>(FIFO);
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
        queue.add(new Entry(order, timestamp, nextSeq++));
    }

    public record QueueSlot(int position, String orderId, OrderStatus status) {}

public synchronized List<QueueSlot> snapshot() {
    List<Entry> sorted = queue.stream().sorted(FIFO).toList();
    List<QueueSlot> slots = new ArrayList<>(sorted.size());
    for (int i = 0; i < sorted.size(); i++) {
        Order o = sorted.get(i).order();
        slots.add(new QueueSlot(i + 1, o.getId(), o.getStatus()));
    }
    return List.copyOf(slots);
}

}
