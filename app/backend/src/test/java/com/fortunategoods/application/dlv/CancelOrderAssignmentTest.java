package com.fortunategoods.application.dlv;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fortunategoods.domain.courier.Assignment;
import com.fortunategoods.domain.courier.CourierStatus;
import com.fortunategoods.domain.courier.DispatchQueue.WaitingOrder;
import com.fortunategoods.domain.courier.PotentialCourier;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.technical.persistence.AssignmentRepository;
import com.fortunategoods.technical.persistence.CourierRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;

import static com.fortunategoods.testsupport.TestData.order;

class CancelOrderAssignmentTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final CourierRepository couriers = mock(CourierRepository.class);
    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final RestaurantRepository restaurants = mock(RestaurantRepository.class);
    private final DispatchService service = new DispatchService(orders, restaurants, couriers, assignments);

    @BeforeEach
    void setup() {
        when(orders.lockWaitingForDispatch()).thenReturn(List.of());
        when(couriers.findIdleWithoutOffer()).thenReturn(List.of());
    }

    @Test
    void acceptedAssignmentReleasesCourierAndOffersOldestWaitingOrder() {
        Order cancelled = givenAssignment(Assignment.Status.ACCEPTED, CourierStatus.ASSIGNED);
        LocalDateTime now = LocalDateTime.now();
        when(orders.lockWaitingForDispatch()).thenReturn(List.of(
            new WaitingOrder(order("102", OrderStatus.READY), now, 802),
            new WaitingOrder(order("101", OrderStatus.READY), now.minusMinutes(5), 801)));
        when(couriers.findIdleWithoutOffer()).thenReturn(List.of(new PotentialCourier("15", 10, 10)));
        when(restaurants.findById("1")).thenReturn(
            new Restaurant("1", "Cafe", RestaurantStatus.PAUSED, 10, 10, List.of()));
        when(assignments.add(any(Assignment.class))).thenAnswer(call -> call.getArgument(0));

        var result = service.cancelOrderAssignment(cancelled);

        assertNull(cancelled.getCourierId());
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertEquals(1, result.offers().size());
        assertEquals("101", result.offers().get(0).getOrderId());
        assertEquals("15", result.offers().get(0).getCourierId());
        assertEquals(Assignment.Status.OFFERED, result.offers().get(0).getStatus());
        assertEquals("102", result.stillWaiting().get(0).order().getId());

        var sequence = inOrder(orders, couriers, assignments);
        sequence.verify(orders).lockWaitingForDispatch();
        sequence.verify(couriers).lockAll();
        sequence.verify(assignments).markCancelled("900");
        sequence.verify(orders).clearCourierAfterCancellation("100", "15");
        sequence.verify(couriers).updateStatusAndPosition("15", CourierStatus.ASSIGNED, CourierStatus.IDLE, null, null);
        sequence.verify(couriers).findIdleWithoutOffer();
    }

    @Test
    void offeredAssignmentEndsWithoutChangingIdleCourier() {
        Order cancelled = givenAssignment(Assignment.Status.OFFERED, CourierStatus.IDLE);

        service.cancelOrderAssignment(cancelled);

        verify(assignments).markCancelled("900");
        verify(orders, never()).clearCourierAfterCancellation(anyString(), anyString());
        verify(couriers, never()).updateStatusAndPosition(anyString(), any(), any(), any(), any());
        verify(couriers).findIdleWithoutOffer();
    }

    @Test
    void offlineCourierStaysOffline() {
        Order cancelled = givenAssignment(Assignment.Status.ACCEPTED, CourierStatus.OFFLINE);

        service.cancelOrderAssignment(cancelled);

        verify(assignments).markCancelled("900");
        verify(orders).clearCourierAfterCancellation("100", "15");
        verify(couriers, never()).updateStatusAndPosition(anyString(), any(), any(), any(), any());
        assertNull(cancelled.getCourierId());
    }

    @Test
    void orderWithoutAssignmentCanStillFinishCancellation() {
        service.cancelOrderAssignment(order("100", OrderStatus.CANCELLED));

        verify(assignments, never()).markCancelled(anyString());
        verify(couriers).findIdleWithoutOffer();
    }

    @ParameterizedTest
    @ValueSource(strings = {"assignment", "order", "courier"})
    void failedUpdatesStopDispatch(String failedUpdate) {
        Order cancelled = givenAssignment(Assignment.Status.ACCEPTED, CourierStatus.ASSIGNED);
        if (failedUpdate.equals("assignment")) when(assignments.markCancelled("900")).thenReturn(false);
        if (failedUpdate.equals("order")) when(orders.clearCourierAfterCancellation("100", "15")).thenReturn(false);
        if (failedUpdate.equals("courier")) {
            when(couriers.updateStatusAndPosition("15", CourierStatus.ASSIGNED, CourierStatus.IDLE, null, null))
                .thenReturn(false);
        }

        var error = assertThrows(DomainRuleViolation.class, () -> service.cancelOrderAssignment(cancelled));

        assertEquals(DomainRuleViolation.Reason.CONFLICT, error.getReason());
        verify(couriers, never()).findIdleWithoutOffer();
    }

    @Test
    void carryingCourierCannotBeReleased() {
        Order cancelled = givenAssignment(Assignment.Status.ACCEPTED, CourierStatus.CARRYING);

        assertThrows(DomainRuleViolation.class, () -> service.cancelOrderAssignment(cancelled));

        verify(assignments, never()).markCancelled(anyString());
        verify(couriers, never()).findIdleWithoutOffer();
    }

    @Test
    void missingCourierStopsCancellation() {
        Order cancelled = givenAssignment(Assignment.Status.ACCEPTED, CourierStatus.ASSIGNED);
        when(couriers.lockStatusById("15")).thenReturn(null);

        var error = assertThrows(DomainRuleViolation.class, () -> service.cancelOrderAssignment(cancelled));

        assertEquals(DomainRuleViolation.Reason.NOT_FOUND, error.getReason());
        verify(assignments, never()).markCancelled(anyString());
        verify(couriers, never()).findIdleWithoutOffer();
    }

    @Test
    void courierBindingWithoutAssignmentIsRefused() {
        Order cancelled = order("100", OrderStatus.CANCELLED);
        cancelled.setCourierId("15");

        var error = assertThrows(DomainRuleViolation.class, () -> service.cancelOrderAssignment(cancelled));

        assertEquals(DomainRuleViolation.Reason.CONFLICT, error.getReason());
        verify(couriers, never()).findIdleWithoutOffer();
    }

    @Test
    void cleanupRequiresACancelledOrder() {
        for (Order existing : new Order[] {null, order("100", OrderStatus.READY)}) {
            assertThrows(DomainRuleViolation.class, () -> service.cancelOrderAssignment(existing));
        }
        verifyNoInteractions(orders, couriers, assignments, restaurants);
    }

    private Order givenAssignment(Assignment.Status status, CourierStatus courierStatus) {
        Order cancelled = order("100", OrderStatus.CANCELLED);
        if (status == Assignment.Status.ACCEPTED) cancelled.setCourierId("15");
        when(assignments.findActiveByOrder("100")).thenReturn(
            new Assignment("900", "100", "15", status, false, false));
        when(couriers.lockStatusById("15")).thenReturn(courierStatus);
        when(assignments.markCancelled("900")).thenReturn(true);
        when(orders.clearCourierAfterCancellation("100", "15")).thenReturn(true);
        when(couriers.updateStatusAndPosition("15", CourierStatus.ASSIGNED, CourierStatus.IDLE, null, null))
            .thenReturn(true);
        return cancelled;
    }
}
