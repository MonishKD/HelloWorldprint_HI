package com.fortunategoods.application.dlv;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fortunategoods.domain.courier.Assignment;
import com.fortunategoods.domain.courier.CourierStatus;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderEvent;
import com.fortunategoods.domain.order.OrderFinished;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.AssignmentRepository;
import com.fortunategoods.technical.persistence.CourierRepository;
import com.fortunategoods.technical.persistence.OrderEventRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;

import static com.fortunategoods.testsupport.TestData.order;

class DeliveryServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderEventRepository events = mock(OrderEventRepository.class);
    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final CourierRepository couriers = mock(CourierRepository.class);
    private final RestaurantRepository restaurants = mock(RestaurantRepository.class);
    private final DispatchService dispatch = mock(DispatchService.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final DeliveryService service = new DeliveryService(
        orders, events, assignments, couriers, restaurants, dispatch, publisher);

    private Order given(OrderStatus status, CourierStatus courierStatus) {
        Order existing = new Order("100", "7", "1", "15", status, null, "Home",
            46.0, -74.0, null, order("100", status).getLines());
        when(orders.findById("100")).thenReturn(existing);
        when(assignments.findActiveByCourier("15")).thenReturn(new Assignment(
            "900", "100", "15", Assignment.Status.ACCEPTED, status == OrderStatus.PICKED_UP, false));
        when(couriers.lockStatusById("15")).thenReturn(courierStatus);
        when(restaurants.findById("1")).thenReturn(new Restaurant(
            "1", "Cafe", RestaurantStatus.PAUSED, 45.5, -73.6, List.of()));
        when(events.findByOrderId("100")).thenReturn(List.of(
            new OrderEvent("700", "100", null, null, status, LocalDateTime.now())));
        when(orders.updateStatusIfUnchanged(eq("100"), eq("700"), any(), any())).thenReturn("701");
        when(assignments.markPickedUp("900")).thenReturn(true);
        when(assignments.markDelivered("900")).thenReturn(true);
        when(couriers.updateStatusAndPosition(eq("15"), any(), any(), any(), any())).thenReturn(true);
        return existing;
    }

    @Test
    void pickupUpdatesOrderAssignmentAndCourier() {
        Order existing = given(OrderStatus.READY, CourierStatus.ASSIGNED);

        var result = service.confirmPickup("15", UserRole.COURIER);

        assertEquals(OrderStatus.PICKED_UP, existing.getStatus());
        assertEquals(701L, result.getEventId());
        verify(assignments).markPickedUp("900");
        verify(couriers).updateStatusAndPosition("15", CourierStatus.ASSIGNED,
            CourierStatus.CARRYING, 45.5, -73.6);
        verifyNoInteractions(dispatch, publisher);
    }

    @Test
    void deliveryFreesCourierRetriesDispatchAndPublishesEvent() {
        Order existing = given(OrderStatus.PICKED_UP, CourierStatus.CARRYING);

        var result = service.confirmDelivery("15", UserRole.COURIER);

        assertEquals(OrderStatus.DELIVERED, existing.getStatus());
        assertEquals(701L, result.getEventId());
        verify(assignments).markDelivered("900");
        var sequence = inOrder(orders, couriers, dispatch, publisher);
        sequence.verify(orders).lockWaitingForDispatch();
        sequence.verify(couriers).lockAll();
        sequence.verify(couriers).updateStatusAndPosition("15", CourierStatus.CARRYING,
            CourierStatus.IDLE, 46.0, -74.0);
        sequence.verify(dispatch).retryDispatch(List.of());
        ArgumentCaptor<OrderFinished> captured = ArgumentCaptor.forClass(OrderFinished.class);
        sequence.verify(publisher).publishEvent(captured.capture());
        assertEquals("100", captured.getValue().getOrderId());
        assertEquals(OrderStatus.DELIVERED, captured.getValue().getFinalStatus());
    }

    @Test
    void missingDestinationCoordinatesKeepCurrentPosition() {
        Order existing = given(OrderStatus.PICKED_UP, CourierStatus.CARRYING);
        when(orders.findById("100")).thenReturn(new Order("100", "7", "1", "15",
            OrderStatus.PICKED_UP, null, null, null, null, null, existing.getLines()));

        service.confirmDelivery("15", UserRole.COURIER);

        verify(couriers).updateStatusAndPosition("15", CourierStatus.CARRYING,
            CourierStatus.IDLE, null, null);
    }

    @Test
    void otherRolesAreRefusedBeforeReadingAnything() {
        for (UserRole role : UserRole.values()) {
            if (role == UserRole.COURIER) continue;
            assertEquals(DomainRuleViolation.Reason.FORBIDDEN, assertThrows(DomainRuleViolation.class,
                () -> service.confirmPickup("15", role)).getReason());
            assertEquals(DomainRuleViolation.Reason.FORBIDDEN, assertThrows(DomainRuleViolation.class,
                () -> service.confirmDelivery("15", role)).getReason());
        }
        verifyNoInteractions(orders, assignments, couriers, events, publisher, dispatch);
    }

    @Test
    void invalidCourierIdsAreRefusedBeforeReadingAnything() {
        for (String id : new String[] {null, "", "0", "abc", "2147483648"}) {
            assertEquals(DomainRuleViolation.Reason.INVALID, assertThrows(DomainRuleViolation.class,
                () -> service.confirmPickup(id, UserRole.COURIER)).getReason());
            assertEquals(DomainRuleViolation.Reason.INVALID, assertThrows(DomainRuleViolation.class,
                () -> service.confirmDelivery(id, UserRole.COURIER)).getReason());
        }
        verifyNoInteractions(orders, assignments, couriers, events, publisher, dispatch);
    }

    @Test
    void missingOrChangedAssignmentIsRefused() {
        assertEquals(DomainRuleViolation.Reason.CONFLICT, assertThrows(DomainRuleViolation.class,
            () -> service.confirmPickup("15", UserRole.COURIER)).getReason());
        given(OrderStatus.READY, CourierStatus.ASSIGNED);
        when(assignments.findActiveByCourier("15")).thenReturn(
            new Assignment("900", "100", "15", Assignment.Status.ACCEPTED, false, false),
            new Assignment("901", "101", "15", Assignment.Status.ACCEPTED, false, false));

        assertEquals(DomainRuleViolation.Reason.CONFLICT, assertThrows(DomainRuleViolation.class,
            () -> service.confirmPickup("15", UserRole.COURIER)).getReason());
        verifyNoInteractions(events, publisher, dispatch);
    }

    @Test
    void anotherCouriersAssignmentIsForbidden() {
        given(OrderStatus.READY, CourierStatus.ASSIGNED);
        when(assignments.findActiveByCourier("15")).thenReturn(
            new Assignment("900", "100", "16", Assignment.Status.ACCEPTED, false, false));

        assertEquals(DomainRuleViolation.Reason.FORBIDDEN, assertThrows(DomainRuleViolation.class,
            () -> service.confirmPickup("15", UserRole.COURIER)).getReason());
        verifyNoInteractions(events, publisher, dispatch);
    }

    @Test
    void wrongOrderOrCourierStatesAreRefused() {
        given(OrderStatus.PREPARING, CourierStatus.ASSIGNED);
        assertThrows(DomainRuleViolation.class, () -> service.confirmPickup("15", UserRole.COURIER));
        given(OrderStatus.READY, CourierStatus.ASSIGNED);
        assertThrows(DomainRuleViolation.class, () -> service.confirmDelivery("15", UserRole.COURIER));
        for (CourierStatus status : List.of(CourierStatus.OFFLINE, CourierStatus.IDLE, CourierStatus.CARRYING)) {
            given(OrderStatus.READY, status);
            assertThrows(DomainRuleViolation.class, () -> service.confirmPickup("15", UserRole.COURIER));
        }
        for (CourierStatus status : List.of(CourierStatus.OFFLINE, CourierStatus.IDLE, CourierStatus.ASSIGNED)) {
            given(OrderStatus.PICKED_UP, status);
            assertThrows(DomainRuleViolation.class, () -> service.confirmDelivery("15", UserRole.COURIER));
        }
        verifyNoInteractions(events, publisher, dispatch);
    }

    @Test
    void failedOrderSaveDoesNotPublishOrRetry() {
        given(OrderStatus.PICKED_UP, CourierStatus.CARRYING);
        when(orders.updateStatusIfUnchanged("100", "700", OrderStatus.PICKED_UP, OrderStatus.DELIVERED)).thenReturn(null);

        assertThrows(DomainRuleViolation.class, () -> service.confirmDelivery("15", UserRole.COURIER));

        verify(assignments, never()).markDelivered(anyString());
        verify(couriers, never()).updateStatusAndPosition(anyString(), any(), any(), any(), any());
        verifyNoInteractions(dispatch, publisher);
    }

    @Test
    void failedCourierUpdateDoesNotPublishOrRetry() {
        given(OrderStatus.PICKED_UP, CourierStatus.CARRYING);
        when(couriers.updateStatusAndPosition(eq("15"), any(), any(), any(), any())).thenReturn(false);

        assertThrows(DomainRuleViolation.class, () -> service.confirmDelivery("15", UserRole.COURIER));

        verifyNoInteractions(dispatch, publisher);
    }
}
