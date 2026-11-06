package com.fortunategoods.application.dlv;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fortunategoods.domain.courier.Assignment;
import com.fortunategoods.domain.courier.CourierStatus;
import com.fortunategoods.domain.courier.DispatchQueue.WaitingOrder;
import com.fortunategoods.domain.courier.PotentialCourier;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderEvent;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.AssignmentRepository;
import com.fortunategoods.technical.persistence.CourierRepository;
import com.fortunategoods.technical.persistence.OrderEventRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;

import static com.fortunategoods.testsupport.TestData.order;

class ReassignmentServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderEventRepository events = mock(OrderEventRepository.class);
    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final CourierRepository couriers = mock(CourierRepository.class);
    private final RestaurantRepository restaurants = mock(RestaurantRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final DispatchService dispatch = new DispatchService(orders, restaurants, couriers, assignments);
    private final ReassignmentService service = new ReassignmentService(
        orders, events, assignments, couriers, restaurants, users, dispatch);
    private final LocalDateTime noon = LocalDateTime.of(2026, 11, 6, 12, 0);

    private Order givenStalledOrder() {
        Order existing = order("100", OrderStatus.READY);
        existing.setCourierId("15");
        when(orders.findById("100")).thenReturn(existing);
        when(assignments.findActiveByOrder("100")).thenReturn(
            new Assignment("900", "100", "15", Assignment.Status.ACCEPTED, false, false));
        when(couriers.lockStatusById("15")).thenReturn(CourierStatus.OFFLINE);
        when(events.findByOrderId("100")).thenReturn(List.of(
            new OrderEvent("700", "100", null, null, OrderStatus.READY, noon)));
        when(assignments.markReassigned("900")).thenReturn(true);
        when(orders.clearCourierIfUnchanged("100", "15")).thenReturn(true);
        when(restaurants.findById("1")).thenReturn(
            new Restaurant("1", "Cafe", RestaurantStatus.PAUSED, 10, 10, List.of()));
        when(couriers.findIdleWithoutOffer()).thenReturn(List.of(new PotentialCourier("2", 11, 10)));
        when(assignments.add(any(Assignment.class))).thenAnswer(call -> call.getArgument(0));
        return existing;
    }

    @Test
    void reassignsAndKeepsOldCourierOffline() {
        Order existing = givenStalledOrder();

        var result = service.reassignOrder("9", UserRole.OPERATOR, "100");

        assertEquals("2", result.getOfferedCourierId());
        assertEquals("offered", result.getDispatchStatus());
        assertEquals(OrderStatus.READY, existing.getStatus());
        assertNull(existing.getCourierId());
        verify(assignments).markReassigned("900");
        verify(orders).clearCourierIfUnchanged("100", "15");
        verify(couriers, never()).updateStatusAndPosition(anyString(), any(), any(), any(), any());
        verify(events, never()).add(anyString(), anyString(), any(), any());
    }

    @Test
    void noIdleCourierLeavesTheOrderWaiting() {
        givenStalledOrder();
        when(couriers.findIdleWithoutOffer()).thenReturn(List.of());

        var result = service.reassignOrder("9", UserRole.OPERATOR, "100");

        assertNull(result.getOfferedCourierId());
        assertEquals("waiting", result.getDispatchStatus());
        verify(assignments, never()).add(any());
    }

    @Test
    void olderWaitingOrderGetsTheCourierFirst() {
        givenStalledOrder();
        when(orders.lockWaitingForDispatch()).thenReturn(List.of(
            new WaitingOrder(order("99", OrderStatus.READY), noon.minusMinutes(1), 699)));

        assertEquals("waiting", service.reassignOrder("9", UserRole.OPERATOR, "100").getDispatchStatus());

        verify(assignments).add(argThat(offer -> offer.getOrderId().equals("99")));
    }

    @Test
    void otherRolesAreRefusedBeforeReadingAnything() {
        for (UserRole role : UserRole.values()) {
            if (role == UserRole.OPERATOR) continue;
            assertEquals(DomainRuleViolation.Reason.FORBIDDEN, assertThrows(DomainRuleViolation.class,
                () -> service.reassignOrder("9", role, "100")).getReason());
            assertThrows(DomainRuleViolation.class, () -> service.stalledOrders("9", role));
        }
        verifyNoInteractions(orders, assignments, couriers, events, restaurants, users);
    }

    @Test
    void invalidIdsAreRefusedBeforeReadingAnything() {
        for (String id : new String[] {null, "", "0", "abc", "2147483648"}) {
            assertEquals(DomainRuleViolation.Reason.INVALID, assertThrows(DomainRuleViolation.class,
                () -> service.reassignOrder("9", UserRole.OPERATOR, id)).getReason());
            assertEquals(DomainRuleViolation.Reason.INVALID, assertThrows(DomainRuleViolation.class,
                () -> service.reassignOrder(id, UserRole.OPERATOR, "100")).getReason());
            assertEquals(DomainRuleViolation.Reason.INVALID, assertThrows(DomainRuleViolation.class,
                () -> service.stalledOrders(id, UserRole.OPERATOR)).getReason());
        }
        verifyNoInteractions(orders, assignments, couriers, events, restaurants, users);
    }

    @Test
    void nonStalledOrderOrMissingAssignmentIsRefused() {
        givenStalledOrder();
        when(couriers.lockStatusById("15")).thenReturn(CourierStatus.ASSIGNED);
        assertEquals(DomainRuleViolation.Reason.CONFLICT, assertThrows(DomainRuleViolation.class,
            () -> service.reassignOrder("9", UserRole.OPERATOR, "100")).getReason());

        when(assignments.findActiveByOrder("100")).thenReturn(null);
        assertEquals(DomainRuleViolation.Reason.CONFLICT, assertThrows(DomainRuleViolation.class,
            () -> service.reassignOrder("9", UserRole.OPERATOR, "100")).getReason());
        verify(assignments, never()).markReassigned(anyString());
        verify(orders, never()).clearCourierIfUnchanged(anyString(), anyString());
    }

    @Test
    void missingOrderIsNotFound() {
        assertEquals(DomainRuleViolation.Reason.NOT_FOUND, assertThrows(DomainRuleViolation.class,
            () -> service.reassignOrder("9", UserRole.OPERATOR, "100")).getReason());
        verifyNoInteractions(assignments, couriers, events);
    }

    @Test
    void failedSaveCreatesNoNewOffer() {
        givenStalledOrder();
        when(orders.clearCourierIfUnchanged("100", "15")).thenReturn(false);

        assertThrows(DomainRuleViolation.class, () -> service.reassignOrder("9", UserRole.OPERATOR, "100"));

        verify(assignments, never()).add(any());
    }

    @Test
    void stalledReadReturnsRestaurantAndCourierNames() {
        Order existing = givenStalledOrder();
        when(orders.findStalledOrders()).thenReturn(List.of(existing));
        when(users.findCourierName("15")).thenReturn("Carl");

        var result = service.stalledOrders("9", UserRole.OPERATOR);

        assertEquals("100", result.get(0).getOrderId());
        assertEquals("Cafe", result.get(0).getRestaurantName());
        assertEquals("Carl", result.get(0).getCourierName());
        verify(assignments, never()).markReassigned(anyString());
    }
}
