package com.fortunategoods.domain.courier;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import com.fortunategoods.domain.courier.DispatchQueue.WaitingOrder;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrderStatus;

import static com.fortunategoods.testsupport.TestData.order;

class DispatchQueueTest {
    private static final LocalDateTime NOON = LocalDateTime.of(2026, 11, 5, 12, 0);

    @Test
    void oldestReadyOrderComesFirstThenLowestEventId() {
        DispatchQueue queue = new DispatchQueue(List.of(
            new WaitingOrder(order("102", OrderStatus.READY), NOON.plusMinutes(1), 700),
            new WaitingOrder(order("101", OrderStatus.READY), NOON, 702),
            new WaitingOrder(order("100", OrderStatus.READY), NOON, 701)));

        assertEquals(List.of("100", "101", "102"), queue.snapshot().stream().map(w -> w.order().getId()).toList());
    }

    @Test
    void onlyReadyOrdersCanWait() {
        WaitingOrder notReady = new WaitingOrder(order("100", OrderStatus.PREPARING), NOON, 700);

        DomainRuleViolation error = assertThrows(DomainRuleViolation.class, () -> new DispatchQueue(List.of(notReady)));

        assertEquals(DomainRuleViolation.Reason.CONFLICT, error.getReason());
    }
}
