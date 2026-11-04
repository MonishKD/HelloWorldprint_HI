package com.fortunategoods.application.ord;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.springframework.context.ApplicationEventPublisher;

import com.fortunategoods.application.ord.orders.OrderService;
import com.fortunategoods.application.ord.orders.preparation.RegisterPreparationQueue;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderEvent;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.OrderEventRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;
import static com.fortunategoods.testsupport.TestData.order;

public class AcceptOrderServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderEventRepository events = mock(OrderEventRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RegisterPreparationQueue preparationQueue = mock(RegisterPreparationQueue.class);
    private final OrderService service = new OrderService(mock(RestaurantRepository.class), orders, events, users, preparationQueue, mock(ApplicationEventPublisher.class));

    private Order order;

    @BeforeEach
    void setup() {
        order = order("100", OrderStatus.PLACED);
        when(orders.findById("100")).thenReturn(order);
        when(users.findRestaurantIdByUserId("8")).thenReturn("1");
        when(events.findByOrderId("100")).thenReturn(List.of(new OrderEvent("700", "100", null, null, OrderStatus.PLACED, LocalDateTime.now())));
    }

    @Test
    void staffCanAcceptOnlyOnce() {
        when(orders.updateStatusIfUnchanged("100", "700", OrderStatus.PLACED, OrderStatus.ACCEPTED)).thenReturn("701");

        var result = service.acceptOrder("8", UserRole.RESTAURANT, "100");

        assertEquals("100", result.getOrderId());
        assertEquals(OrderStatus.ACCEPTED, result.getStatus());
        assertEquals(701L, result.getEventId());
        assertEquals(OrderStatus.ACCEPTED, order.getStatus());

        assertFailure(DomainRuleViolation.Reason.CONFLICT);

        verify(orders, times(1)).updateStatusIfUnchanged("100", "700", OrderStatus.PLACED, OrderStatus.ACCEPTED);
    }

    @Test
    void wrongRestaurantForbidden() {
        for (String restaurantId : new String[] {"2", null}) {
            when(users.findRestaurantIdByUserId("8")).thenReturn(restaurantId);
            assertFailure(DomainRuleViolation.Reason.FORBIDDEN);
            assertEquals(OrderStatus.PLACED, order.getStatus());
        }
        verifyNothingSaved();
    }

    @Test
    void customerCannotAccept() {
        DomainRuleViolation error = assertThrows(DomainRuleViolation.class,() -> service.acceptOrder("7", UserRole.CUSTOMER, "100"));
        assertEquals(DomainRuleViolation.Reason.FORBIDDEN, error.getReason());
        verifyNoInteractions(orders, events, users);
    }

    @Test
    void missingOrderFromRepoRejected() {
        when(orders.findById("100")).thenReturn(null);
        assertFailure(DomainRuleViolation.Reason.NOT_FOUND);
        verifyNothingSaved();
    }

    @Test
    void otherStatusesCannotBeAccepted() {
        for (OrderStatus status : OrderStatus.values()) {
            if (status == OrderStatus.PLACED) {
                continue;
            }
            Order existing = order("100", status);
            when(orders.findById("100")).thenReturn(existing);

            assertFailure(DomainRuleViolation.Reason.CONFLICT);
            assertEquals(status, existing.getStatus());
        }
        verifyNothingSaved();
    }

    @Test
    void concurrentChangeIsRejected() {
        // The mocked update returns null, meaning the database order changed.
        assertFailure(DomainRuleViolation.Reason.CONFLICT);
        verify(orders).updateStatusIfUnchanged("100", "700", OrderStatus.PLACED, OrderStatus.ACCEPTED);
    }

    private void assertFailure(DomainRuleViolation.Reason reason) {
        DomainRuleViolation error = assertThrows(DomainRuleViolation.class,() -> service.acceptOrder("8", UserRole.RESTAURANT, "100"));
        assertEquals(reason, error.getReason());
    }

    private void verifyNothingSaved() {
        verify(orders, never()).updateStatusIfUnchanged(anyString(), anyString(), any(), any());
    }
}
