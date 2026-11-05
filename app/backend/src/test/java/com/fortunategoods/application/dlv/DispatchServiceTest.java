package com.fortunategoods.application.dlv;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fortunategoods.application.dlv.DispatchService.DispatchResult;
import com.fortunategoods.application.dlv.queue.DispatchQueueEntry;
import com.fortunategoods.domain.courier.Assignment;
import com.fortunategoods.domain.courier.DispatchQueue.WaitingOrder;
import com.fortunategoods.domain.courier.PotentialCourier;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.domain.user.UserRole;
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

    private static final LocalDateTime NOON = LocalDateTime.of(2026, 11, 5, 12, 0);

    private void givenReadyOrder() {
        when(orders.findById("100")).thenReturn(order("100", OrderStatus.READY));
        givenRestaurant();
    }

    private void givenRestaurant() {
        when(restaurants.findById("1")).thenReturn(
            new Restaurant("1", "Cafe", RestaurantStatus.PAUSED, 10, 10, List.of()));
    }

    private void givenWaiting(WaitingOrder... waiting) {
        when(orders.findWaitingForDispatch()).thenReturn(List.of(waiting));
    }

    private void givenIdle(PotentialCourier... idle) {
        when(couriers.findIdleWithoutOffer()).thenReturn(List.of(idle));
        when(assignments.add(any(Assignment.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static WaitingOrder waiting(String id, int minutesAfterNoon) {
        return new WaitingOrder(order(id, OrderStatus.READY), NOON.plusMinutes(minutesAfterNoon), 700 + minutesAfterNoon);
    }

    private static List<String> offered(DispatchResult result) {
        return result.offers().stream().map(a -> a.getOrderId() + "->" + a.getCourierId()).toList();
    }

    @Test
    void offersNearestCourierEvenWhenRestaurantIsPaused() {
        givenReadyOrder();
        givenWaiting(waiting("100", 0));
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

    @Test
    void courierGoingOnDutyGetsTheWaitingOrder() {
        givenRestaurant();
        givenWaiting(waiting("100", 0));
        givenIdle(new PotentialCourier("15", 11, 10));

        assertEquals(List.of("100->15"), offered(service.retryDispatch()));
    }

    @Test
    void eachIdleCourierGetsOneOrderOldestFirst() {
        givenRestaurant();
        givenWaiting(waiting("100", 0), waiting("101", 1), waiting("102", 2));
        givenIdle(new PotentialCourier("2", 20, 20), new PotentialCourier("15", 11, 10));

        DispatchResult result = service.retryDispatch();

        // The oldest order gets the nearest courier, the next one gets whoever is left.
        assertEquals(List.of("100->15", "101->2"), offered(result));
        assertEquals("102", result.stillWaiting().get(0).order().getId());
    }

    @Test
    void newlyReadyOrderDoesNotTakeTheCourierFromAnOlderOne() {
        givenReadyOrder();
        givenWaiting(waiting("99", 0), waiting("100", 3));
        givenIdle(new PotentialCourier("15", 11, 10));

        assertNull(service.offerReadyOrder("100"));

        ArgumentCaptor<Assignment> captured = ArgumentCaptor.forClass(Assignment.class);
        verify(assignments).add(captured.capture());
        assertEquals("99", captured.getValue().getOrderId());
    }

    @Test
    void readingTheQueueOffersNothing() {
        givenRestaurant();
        givenWaiting(waiting("100", 0));

        DispatchQueueEntry first = service.dispatchQueue(UserRole.OPERATOR).get(0);

        assertEquals("100", first.orderId());
        assertEquals("Cafe", first.restaurantName());
        verifyNoInteractions(couriers, assignments);
    }
}
