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

    @Test
    void cancellationReleasesAssignedCouriersAndKeepsOfflineCouriersOffline() {
        Order cancelled = order("100", OrderStatus.CANCELLED);
        cancelled.setCourierId("15");
        Assignment accepted = assignment(Assignment.Status.ACCEPTED, false, false);
        assertEquals(CourierStatus.IDLE, accepted.courierStatusAfterCancellation(cancelled, CourierStatus.ASSIGNED));
        assertEquals(CourierStatus.OFFLINE, accepted.courierStatusAfterCancellation(cancelled, CourierStatus.OFFLINE));

        cancelled.setCourierId(null);
        Assignment offered = assignment(Assignment.Status.OFFERED, false, false);
        assertEquals(CourierStatus.IDLE, offered.courierStatusAfterCancellation(cancelled, CourierStatus.IDLE));
        assertEquals(CourierStatus.OFFLINE, offered.courierStatusAfterCancellation(cancelled, CourierStatus.OFFLINE));
    }

    @Test
    void endedOrPickedUpAssignmentsCannotBeCancelled() {
        Order cancelled = order("100", OrderStatus.CANCELLED);
        cancelled.setCourierId("15");
        for (Assignment.Status status : Assignment.Status.values()) {
            if (status == Assignment.Status.OFFERED || status == Assignment.Status.ACCEPTED) continue;
            assertThrows(DomainRuleViolation.class, () -> assignment(status, false, false)
                .courierStatusAfterCancellation(cancelled, CourierStatus.ASSIGNED));
        }
        assertThrows(DomainRuleViolation.class, () -> assignment(Assignment.Status.ACCEPTED, true, false)
            .courierStatusAfterCancellation(cancelled, CourierStatus.CARRYING));
        assertThrows(DomainRuleViolation.class, () -> assignment(Assignment.Status.ACCEPTED, false, true)
            .courierStatusAfterCancellation(cancelled, CourierStatus.ASSIGNED));
    }

    @Test
    void cancellationRequiresMatchingOrderAndCourierState() {
        Order cancelled = order("100", OrderStatus.CANCELLED);
        cancelled.setCourierId("15");
        Assignment accepted = assignment(Assignment.Status.ACCEPTED, false, false);
        assertThrows(DomainRuleViolation.class, () -> accepted.courierStatusAfterCancellation(cancelled, CourierStatus.IDLE));
        assertThrows(DomainRuleViolation.class, () -> accepted.courierStatusAfterCancellation(null, CourierStatus.ASSIGNED));
        assertThrows(DomainRuleViolation.class, () -> accepted.courierStatusAfterCancellation(order("100", OrderStatus.READY), CourierStatus.ASSIGNED));
        assertThrows(DomainRuleViolation.class, () -> accepted.courierStatusAfterCancellation(order("101", OrderStatus.CANCELLED), CourierStatus.ASSIGNED));
        cancelled.setCourierId("16");
        assertThrows(DomainRuleViolation.class, () -> accepted.courierStatusAfterCancellation(cancelled, CourierStatus.ASSIGNED));

        Assignment offered = assignment(Assignment.Status.OFFERED, false, false);
        assertThrows(DomainRuleViolation.class, () -> offered.courierStatusAfterCancellation(cancelled, CourierStatus.IDLE));
        cancelled.setCourierId(null);
        assertThrows(DomainRuleViolation.class, () -> offered.courierStatusAfterCancellation(cancelled, CourierStatus.ASSIGNED));
    }

    private Assignment assignment(Assignment.Status status, boolean pickedUp, boolean delivered) {
        return new Assignment("900", "100", "15", status, pickedUp, delivered);
    }
}
