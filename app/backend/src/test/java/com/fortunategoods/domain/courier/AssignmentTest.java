package com.fortunategoods.domain.courier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderStatus;

import static com.fortunategoods.testsupport.TestData.order;

class AssignmentTest {
    @Test
    void acceptedAssignmentAllowsPickup() {
        Assignment assignment = assignment(Assignment.Status.ACCEPTED, false, false);
        assertDoesNotThrow(() -> assignment.requirePickupAllowed("15"));
    }

    @Test
    void pickedUpAssignmentAllowsDelivery() {
        Assignment assignment = assignment(Assignment.Status.ACCEPTED, true, false);
        assertDoesNotThrow(() -> assignment.requireDeliveryAllowed("15"));
    }

    @Test
    void anotherCourierOrMissingCourierIsForbidden() {
        Assignment pickup = assignment(Assignment.Status.ACCEPTED, false, false);
        Assignment delivery = assignment(Assignment.Status.ACCEPTED, true, false);

        // Check if error thrown are valid for each pickup/delivery methods
        for (String courierId : new String[] {"16", null}) {
            var pickupError = assertThrows(DomainRuleViolation.class,() -> pickup.requirePickupAllowed(courierId));
            var deliveryError = assertThrows(DomainRuleViolation.class,() -> delivery.requireDeliveryAllowed(courierId));
            assertEquals(DomainRuleViolation.Reason.FORBIDDEN, pickupError.getReason());
            assertEquals(DomainRuleViolation.Reason.FORBIDDEN, deliveryError.getReason());
        }
    }

    @Test
    void unacceptedOrEndedAssignmentsAreRefused() {
        for (Assignment.Status status : Assignment.Status.values()) {
            if (status == Assignment.Status.ACCEPTED) continue;
            Assignment pickup = assignment(status, false, false);
            Assignment delivery = assignment(status, true, false);

            // Check if error thrown are valid for every status except ACCEPTED
            var pickupError = assertThrows(DomainRuleViolation.class,() -> pickup.requirePickupAllowed("15"));
            var deliveryError = assertThrows(DomainRuleViolation.class,() -> delivery.requireDeliveryAllowed("15"));
            assertEquals(DomainRuleViolation.Reason.CONFLICT, pickupError.getReason());
            assertEquals(DomainRuleViolation.Reason.CONFLICT, deliveryError.getReason());
        }
    }

    @Test
    void handoffsCannotBeRepeatedOrDoneOutOfOrder() {
        Assignment beforePickup = assignment(Assignment.Status.ACCEPTED, false, false);
        Assignment afterPickup = assignment(Assignment.Status.ACCEPTED, true, false);
        Assignment afterDelivery = assignment(Assignment.Status.ACCEPTED, true, true);

        // Check possible combos between pickup/delivery
        var earlyDelivery = assertThrows(DomainRuleViolation.class,() -> beforePickup.requireDeliveryAllowed("15"));
        var repeatedPickup = assertThrows(DomainRuleViolation.class,() -> afterPickup.requirePickupAllowed("15"));
        var pickupAfterDelivery = assertThrows(DomainRuleViolation.class,() -> afterDelivery.requirePickupAllowed("15"));
        var repeatedDelivery = assertThrows(DomainRuleViolation.class,() -> afterDelivery.requireDeliveryAllowed("15"));

        assertEquals(DomainRuleViolation.Reason.CONFLICT, earlyDelivery.getReason());
        assertEquals(DomainRuleViolation.Reason.CONFLICT, repeatedPickup.getReason());
        assertEquals(DomainRuleViolation.Reason.CONFLICT, pickupAfterDelivery.getReason());
        assertEquals(DomainRuleViolation.Reason.CONFLICT, repeatedDelivery.getReason());
    }

    @Test
    void orderMustMatchAssignmentAndCourier() {
        Assignment assignment = assignment(Assignment.Status.ACCEPTED, false, false);
        Order existing = order("100", OrderStatus.READY);
        existing.setCourierId("15");
        assertDoesNotThrow(() -> assignment.requireOrder(existing));

        existing.setCourierId("16");
        assertThrows(DomainRuleViolation.class, () -> assignment.requireOrder(existing));
        Order other = order("101", OrderStatus.READY);
        other.setCourierId("15");
        assertThrows(DomainRuleViolation.class, () -> assignment.requireOrder(other));
        assertThrows(DomainRuleViolation.class, () -> assignment.requireOrder(null));
    }

    private Assignment assignment(Assignment.Status status, boolean pickedUp, boolean delivered) {
        return new Assignment("900", "100", "15", status, pickedUp, delivered);
    }
}