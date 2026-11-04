package com.fortunategoods.application.ord;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.springframework.context.ApplicationEventPublisher;

import com.fortunategoods.application.ord.orders.OrderService;
import com.fortunategoods.application.dlv.DispatchService;
import com.fortunategoods.application.ord.orders.preparation.RegisterPreparationQueue;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderEvent;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.order.PreparationQueue;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.OrderEventRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;
import static com.fortunategoods.testsupport.TestData.order;

class PrepareOrderServiceTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderEventRepository events = mock(OrderEventRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RegisterPreparationQueue queues = mock(RegisterPreparationQueue.class);
    private final OrderService service = new OrderService(
        mock(RestaurantRepository.class), orders, events, users, queues, mock(ApplicationEventPublisher.class), mock(DispatchService.class)
    );

    private final LocalDateTime time = LocalDateTime.of(2026, 10, 20, 12, 0);
    private PreparationQueue queue;
    private Order order;

    @BeforeEach
    void setup() {
        order = order("100", OrderStatus.ACCEPTED);
        queue = new PreparationQueue("1");
        queue.enqueue(order, time, 700);

        when(orders.findById("100")).thenReturn(order);
        when(users.findRestaurantIdByUserId("8")).thenReturn("1");
        when(queues.forRestaurant("1")).thenReturn(queue);
        when(events.findByOrderId("100")).thenReturn(List.of(
            new OrderEvent("700", "100", null, OrderStatus.PLACED, OrderStatus.ACCEPTED, time)
        ));
    }

    @Test
    void firstOrderCanStartPreparing() {
        when(orders.updateStatusIfUnchanged(
            "100", "700", OrderStatus.ACCEPTED, OrderStatus.PREPARING
        )).thenReturn("701");

        var result = service.prepareOrder("8", UserRole.RESTAURANT, "100");

        assertEquals("100", result.getOrderId());
        assertEquals(OrderStatus.PREPARING, result.getStatus());
        assertEquals(701L, result.getEventId());
        assertEquals(OrderStatus.PREPARING, order.getStatus());
        assertEquals(0, queue.snapshot().size());
        verify(orders).updateStatusIfUnchanged(
            "100", "700", OrderStatus.ACCEPTED, OrderStatus.PREPARING
        );
    }

    @Test
    void orderBehindHeadIsRefusedWithoutChanges() {
        queue.enqueue(order("99", OrderStatus.ACCEPTED), time.minusMinutes(1), 699);
        var before = queue.snapshot();

        assertFifoRefusal();

        assertEquals(before, queue.snapshot());
    }

    @Test
    void emptyQueueIsRefusedWithoutChanges() {
        queue.dequeue();

        assertFifoRefusal();

        assertEquals(0, queue.snapshot().size());
    }

    @Test
    void preparingOrderCannotStartAgain() {
        order.startPreparing();

        DomainRuleViolation error = assertThrows(
            DomainRuleViolation.class,
            () -> service.prepareOrder("8", UserRole.RESTAURANT, "100")
        );

        assertEquals(DomainRuleViolation.Reason.CONFLICT, error.getReason());
        assertEquals("Only accepted orders can enter preparation", error.getMessage());
        assertEquals(OrderStatus.PREPARING, order.getStatus());
        verifyNoInteractions(queues, events);
        verify(orders, never()).updateStatusIfUnchanged(anyString(), anyString(), any(), any());
    }

    private void assertFifoRefusal() {
        DomainRuleViolation error = assertThrows(
            DomainRuleViolation.class,
            () -> service.prepareOrder("8", UserRole.RESTAURANT, "100")
        );

        assertEquals(DomainRuleViolation.Reason.CONFLICT, error.getReason());
        assertEquals("Order does not match the next order in the queue for this restaurant!", error.getMessage());
        assertEquals(OrderStatus.ACCEPTED, order.getStatus());
        verify(orders, never()).updateStatusIfUnchanged(anyString(), anyString(), any(), any());
        verifyNoInteractions(events);
    }
}
