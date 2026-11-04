package com.fortunategoods.application.dlv;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fortunategoods.domain.courier.Assignment;
import com.fortunategoods.domain.courier.PotentialCourier;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.technical.persistence.AssignmentRepository;
import com.fortunategoods.technical.persistence.CourierRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;

import static com.fortunategoods.testsupport.TestData.order;

class DispatchServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final RestaurantRepository restaurants = mock(RestaurantRepository.class);
    private final CourierRepository couriers = mock(CourierRepository.class);
    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final DispatchService service = new DispatchService(orders, restaurants, couriers, assignments);

    private void givenReadyOrder() {
        when(orders.findById("100")).thenReturn(order("100", OrderStatus.READY));
        when(restaurants.findById("1")).thenReturn(
            new Restaurant("1", "Cafe", RestaurantStatus.PAUSED, 10, 10, List.of()));
    }

    @Test
    void offersNearestCourierEvenWhenRestaurantIsPaused() {
        givenReadyOrder();
        when(couriers.findIdleWithoutOffer()).thenReturn(List.of(
            new PotentialCourier("2", 20, 20), new PotentialCourier("15", 11, 10)));
        when(assignments.add(any(Assignment.class))).thenReturn(
            new Assignment("900", "100", "15", Assignment.Status.OFFERED, false, false));

        Assignment result = service.offerReadyOrder("100");

        assertEquals("900", result.getId());
        ArgumentCaptor<Assignment> captured = ArgumentCaptor.forClass(Assignment.class);
        verify(assignments).add(captured.capture());
        assertEquals("100", captured.getValue().getOrderId());
        assertEquals("15", captured.getValue().getCourierId());
        assertEquals(Assignment.Status.OFFERED, captured.getValue().getStatus());
        verify(orders).lockOrder("100");
    }

    @Test
    void noAvailableCourierLeavesOrderWaiting() {
        givenReadyOrder();
        when(couriers.findIdleWithoutOffer()).thenReturn(List.of());

        assertNull(service.offerReadyOrder("100"));

        verify(assignments, never()).add(any());
        assertEquals(OrderStatus.READY, orders.findById("100").getStatus());
    }

    @Test
    void ordersMustBeReadyBeforeOffering() {
        for (OrderStatus status : OrderStatus.values()) {
            if (status == OrderStatus.READY) continue;
            when(orders.findById("100")).thenReturn(order("100", status));

            DomainRuleViolation error = assertThrows(DomainRuleViolation.class,
                () -> service.offerReadyOrder("100"));

            assertEquals(DomainRuleViolation.Reason.CONFLICT, error.getReason());
        }
        verifyNoInteractions(couriers, assignments);
    }

    @Test
    void existingOfferIsReturnedWithoutCreatingAnother() {
        givenReadyOrder();
        Assignment existing = new Assignment("900", "100", "15", Assignment.Status.OFFERED, false, false);
        when(assignments.findActiveByOrder("100")).thenReturn(existing);

        assertSame(existing, service.offerReadyOrder("100"));

        verifyNoInteractions(couriers);
        verify(assignments, never()).add(any());
    }
}
