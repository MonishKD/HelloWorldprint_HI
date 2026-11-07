package com.fortunategoods.application.ord;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.security.access.AccessDeniedException;

import com.fortunategoods.application.ord.orders.OrderStatusResponse;
import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandLog;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.ord.orders.OrderService;
import com.fortunategoods.application.ord.orders.OrderServiceResult;
import com.fortunategoods.application.ord.orders.acceptance.AcceptOrderCommand;
import com.fortunategoods.application.ord.orders.acceptance.AcceptOrderResponse;
import com.fortunategoods.application.ord.orders.cancellation.CancelOrderCommand;
import com.fortunategoods.application.ord.orders.placement.PlaceOrderCommand;
import com.fortunategoods.application.ord.orders.placement.PlaceOrderResponse;
import com.fortunategoods.application.ord.orders.ready.ReadyOrderCommand;
import com.fortunategoods.application.ord.orders.ready.ReadyOrderResponse;
import com.fortunategoods.application.ord.orders.rejection.RejectOrderCommand;
import com.fortunategoods.application.ord.orders.rejection.RejectOrderResponse;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.order.validation.RequestedOrderLineItem;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.technical.security.SessionValidator;

// The order commands: who may run each one, and how each turns the OrderService result into its response.
// How the invoker refuses a missing session or a wrong role is tested once in CommandInvokerTest.
class OrderCommandsTest {

    private final OrderService service = mock(OrderService.class);
    private final SessionValidator validator = mock(SessionValidator.class);
    private final CommandLogRepository logs = mock(CommandLogRepository.class);
    private final CommandInvoker invoker = new CommandInvoker(validator, logs);

    private final Session customer = new Session(7, UserRole.CUSTOMER);
    private final Session staff = new Session(8, UserRole.RESTAURANT);

    @Test
    void customerPlacesOrder() {
        List<RequestedOrderLineItem> items = List.of(new RequestedOrderLineItem("10", new BigDecimal("2")));
        when(service.placeOrder("7", "1", items, "No onions", null)).thenReturn(new OrderServiceResult("100", OrderStatus.PLACED, 700));
        PlaceOrderCommand command = new PlaceOrderCommand(service, customer, "1", items, "No onions", null);

        CommandSuccess<PlaceOrderResponse> result = invoker.process(command, customer);

        assertEquals(Set.of(UserRole.CUSTOMER), command.validRoles());
        assertEquals("100", result.getData().getOrderId());
        assertEquals("placed", result.getData().getStatus());
        assertEquals("Order placed.", result.getMsg());
        assertEquals(700L, result.getEventId());
        verify(logs).save(argThat(log -> log.getResult() == CommandLog.Result.SUCCESS && Long.valueOf(700).equals(log.getEventId())));
    }

    @Test
    void customerCancelsOrder() {
        when(service.cancelOrder("7", "100")).thenReturn(new OrderServiceResult("100", OrderStatus.CANCELLED, 701));
        CancelOrderCommand command = new CancelOrderCommand(service, customer, "100");

        CommandSuccess<OrderStatusResponse> result = invoker.process(command, customer);

        assertEquals(Set.of(UserRole.CUSTOMER), command.validRoles());
        assertEquals("100", result.getData().orderId());
        assertEquals("cancelled", result.getData().status());
        assertEquals("Order cancelled.", result.getMsg());
        assertEquals(701L, result.getEventId());
    }

    @Test
    void staffAcceptsOrder() {
        when(service.acceptOrder("8", UserRole.RESTAURANT, "100")).thenReturn(new OrderServiceResult("100", OrderStatus.ACCEPTED, 701));
        AcceptOrderCommand command = new AcceptOrderCommand(service, staff, "100");

        CommandSuccess<AcceptOrderResponse> result = invoker.process(command, staff);

        assertEquals(Set.of(UserRole.RESTAURANT), command.validRoles());
        assertEquals("100", result.getData().getOrderId());
        assertEquals("accepted", result.getData().getStatus());
        assertEquals(701L, result.getEventId());
    }

    @Test
    void staffRejectsOrderWithReason() {
        when(service.rejectOrder("8", UserRole.RESTAURANT, "100", "Sold out"))
            .thenReturn(new OrderServiceResult("100", OrderStatus.REJECTED, 701, "Sold out"));
        RejectOrderCommand command = new RejectOrderCommand(service, staff, "100", "Sold out");

        CommandSuccess<RejectOrderResponse> result = invoker.process(command, staff);

        assertEquals(Set.of(UserRole.RESTAURANT), command.validRoles());
        assertEquals("rejected", result.getData().getStatus());
        assertEquals("Sold out", result.getData().getRejectReason());
        assertEquals(701L, result.getEventId());
    }

    @Test
    void staffMarksOrderReady() {
        when(service.markAsReadyOrder("8", UserRole.RESTAURANT, "100")).thenReturn(new OrderServiceResult("100", OrderStatus.READY, 701));
        ReadyOrderCommand command = new ReadyOrderCommand(service, staff, "100");

        CommandSuccess<ReadyOrderResponse> result = invoker.process(command, staff);

        assertEquals(Set.of(UserRole.RESTAURANT), command.validRoles());
        assertEquals("ready", result.getData().getStatus());
        assertEquals("Order marked ready. No courier is free for it, so it is waiting in the dispatch queue.", result.getMsg());
        assertEquals("waiting", result.getData().getDispatchStatus());
        assertEquals(701L, result.getEventId());
    }

    @Test
    void readyCommandReportsOfferedCourier() {
        when(service.markAsReadyOrder("8", UserRole.RESTAURANT, "100"))
            .thenReturn(new OrderServiceResult("100", OrderStatus.READY, 701, null, "15"));

        var result = new ReadyOrderCommand(service, staff, "100").execute();

        assertEquals("15", result.getData().getOfferedCourierId());
        assertEquals("offered", result.getData().getDispatchStatus());
        assertEquals("Order marked ready. Offered to courier 15", result.getMsg());
    }

    // A refused role never reaches OrderService, for every order command.
    @Test
    void refusedRoleNeverReachesTheService() {
        List<Command<?>> commands = List.of(
                new PlaceOrderCommand(service, staff, "1", List.of(), null, null),
                new CancelOrderCommand(service, staff, "100"),
                new AcceptOrderCommand(service, customer, "100"),
                new RejectOrderCommand(service, customer, "100", "Sold out"),
                new ReadyOrderCommand(service, customer, "100"));
        doThrow(new AccessDeniedException("Forbidden")).when(validator).requireRoles(any(), any());

        for (Command<?> command : commands) {
            assertThrows(AccessDeniedException.class, () -> invoker.process(command, customer));
        }
        verifyNoInteractions(service);
    }
}
