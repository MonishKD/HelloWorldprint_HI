package com.fortunategoods.domain.courier;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderStatus;

/*
    Ready orders that no courier has been offered yet, for the whole platform (R-DLV-04).
    The oldest ready order is offered first. Two orders ready at the same time keep the order
    of their READY events, whose ids only go up.
*/
public class DispatchQueue {

    public record WaitingOrder(Order order, LocalDateTime readySince, long readyEventId) {}

    private static final Comparator<WaitingOrder> OLDEST_READY_FIRST =
        Comparator.comparing(WaitingOrder::readySince).thenComparingLong(WaitingOrder::readyEventId);

    private final List<WaitingOrder> waiting = new ArrayList<>();

    public DispatchQueue(List<WaitingOrder> orders) {
        for (WaitingOrder entry : orders) {
            if (entry == null || entry.order() == null || entry.readySince() == null) {
                throw new IllegalArgumentException("A waiting order needs its order and the time it became ready");
            }
            if (entry.order().getStatus() != OrderStatus.READY) {
                throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Only ready orders can wait for a courier");
            }
            boolean alreadyWaiting = waiting.stream().anyMatch(w -> w.order().getId().equals(entry.order().getId()));
            if (!alreadyWaiting) {
                waiting.add(entry);
            }
        }
        waiting.sort(OLDEST_READY_FIRST);
    }

    public boolean isEmpty() {
        return waiting.isEmpty();
    }

    // Removes and returns the oldest waiting order, or null when nothing is waiting.
    public WaitingOrder takeOldest() {
        return waiting.isEmpty() ? null : waiting.remove(0);
    }

    // Oldest first: position 1 is the next order offered.
    public List<WaitingOrder> snapshot() {
        return List.copyOf(waiting);
    }
}
