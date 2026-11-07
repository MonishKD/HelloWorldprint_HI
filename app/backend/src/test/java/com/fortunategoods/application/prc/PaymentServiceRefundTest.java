package com.fortunategoods.application.prc;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fortunategoods.application.dlv.DispatchService;
import com.fortunategoods.application.ord.orders.OrderService;
import com.fortunategoods.application.ord.orders.preparation.RegisterPreparationQueue;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrderEvent;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.pricing.FareCalculator;
import com.fortunategoods.domain.pricing.LedgerEntry;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.payment.PaymentGateway;
import com.fortunategoods.technical.persistence.LedgerRepository;
import com.fortunategoods.technical.persistence.OrderEventRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;

import static com.fortunategoods.testsupport.TestData.order;

class PaymentServiceRefundTest {
    private static final LocalDateTime NOON = LocalDateTime.of(2026, 11, 5, 12, 0);

    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderEventRepository events = mock(OrderEventRepository.class);
    private final RestaurantRepository restaurants = mock(RestaurantRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final LedgerRepository ledger = mock(LedgerRepository.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final FareCalculator fares = mock(FareCalculator.class);

    private final OrderService orderService = new OrderService(restaurants, orders, events, users, mock(RegisterPreparationQueue.class),
        mock(ApplicationEventPublisher.class), mock(DispatchService.class));
    private final PaymentService service = new PaymentService(gateway, fares, orderService,
        new LedgerService(ledger, orders, events, fares, restaurants, users), orders, restaurants);

    private void givenOrder(OrderStatus status) {
        when(orders.findById("100")).thenReturn(order("100", status));
        when(events.findByOrderId("100")).thenReturn(List.of(new OrderEvent("700", "100", null, null, status, NOON)));
    }

    @Test
    void deliveredOrderGetsARefundEntryThenTheStripeRefund() {
        givenOrder(OrderStatus.DELIVERED);
        when(orders.updateStatusIfUnchanged("100", "700", OrderStatus.DELIVERED, OrderStatus.REFUNDED)).thenReturn("701");
        when(orders.findPaymentId("100")).thenReturn("pi_1");
        when(ledger.findCharge("100")).thenReturn(new LedgerEntry("100", LedgerEntry.Type.CHARGE, OrderStatus.DELIVERED,
            1000, 300, 100, 0, 1400, NOON, NOON, "pi_1"));

        assertEquals(OrderStatus.REFUNDED, service.refundOrder(UserRole.OPERATOR, "100").getStatus());

        ArgumentCaptor<LedgerEntry> saved = ArgumentCaptor.forClass(LedgerEntry.class);
        InOrder inOrder = inOrder(orders, ledger, gateway);
        inOrder.verify(orders).updateStatusIfUnchanged("100", "700", OrderStatus.DELIVERED, OrderStatus.REFUNDED);
        inOrder.verify(ledger).save(saved.capture());
        inOrder.verify(gateway).refund("pi_1");
        assertEquals(LedgerEntry.Type.REFUND, saved.getValue().getType());
        assertEquals(1400, saved.getValue().getAmountCents());
    }

    @Test
    void onlyDeliveredOrdersCanBeRefunded() {
        givenOrder(OrderStatus.READY);

        DomainRuleViolation error = assertThrows(DomainRuleViolation.class, () -> service.refundOrder(UserRole.OPERATOR, "100"));

        assertEquals(DomainRuleViolation.Reason.CONFLICT, error.getReason());
        verifyNoInteractions(ledger, gateway);
    }
}
