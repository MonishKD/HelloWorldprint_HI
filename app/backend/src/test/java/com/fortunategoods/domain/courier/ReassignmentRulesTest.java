package com.fortunategoods.domain.courier;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderStatus;

import static com.fortunategoods.testsupport.TestData.order;

class ReassignmentRulesTest {
    private Order assignedOrder(OrderStatus status) {
        Order existing = order("100", status);
        existing.setCourierId("15");
        return existing;
    }

    @Test
    void onlyOfflineCourierAllowsReassignment() {
        Assignment assignment = new Assignment("900", "100", "15", Assignment.Status.ACCEPTED, false, false);
        Order existing = assignedOrder(OrderStatus.READY);

        assertDoesNotThrow(() -> assignment.requireReassignmentAllowed(existing, CourierStatus.OFFLINE));
        for (CourierStatus status : List.of(CourierStatus.IDLE, CourierStatus.ASSIGNED, CourierStatus.CARRYING)) {
            assertThrows(DomainRuleViolation.class, () -> assignment.requireReassignmentAllowed(existing, status));
        }
    }

    @Test
    void wrongOrderStatesAreRefused() {
        Assignment assignment = new Assignment("900", "100", "15", Assignment.Status.ACCEPTED, false, false);

        for (OrderStatus status : OrderStatus.values()) {
            if (status == OrderStatus.READY) continue;
            Order existing = assignedOrder(status);
            assertThrows(DomainRuleViolation.class, () -> assignment.requireReassignmentAllowed(existing, CourierStatus.OFFLINE));
        }
    }

    @Test
    void unacceptedAndPickedUpAssignmentsAreRefused() {
        Order existing = assignedOrder(OrderStatus.READY);
        for (Assignment.Status status : Assignment.Status.values()) {
            if (status == Assignment.Status.ACCEPTED) continue;
            Assignment assignment = new Assignment("900", "100", "15", status, false, false);
            assertThrows(DomainRuleViolation.class, () -> assignment.requireReassignmentAllowed(existing, CourierStatus.OFFLINE));
        }
        Assignment pickedUp = new Assignment("900", "100", "15", Assignment.Status.ACCEPTED, true, false);
        Assignment delivered = new Assignment("900", "100", "15", Assignment.Status.ACCEPTED, true, true);
        assertThrows(DomainRuleViolation.class, () -> pickedUp.requireReassignmentAllowed(existing, CourierStatus.OFFLINE));
        assertThrows(DomainRuleViolation.class, () -> delivered.requireReassignmentAllowed(existing, CourierStatus.OFFLINE));
    }
}
