package com.fortunategoods.application.ord.orders.ready;

import java.util.Set;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.ord.orders.OrderService;
import com.fortunategoods.application.ord.orders.OrderServiceResult;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.user.UserRole;

public class ReadyOrderCommand implements Command<ReadyOrderResponse> {
    private final OrderService service;
    private final Session session;
    private final String orderId;

    public ReadyOrderCommand(OrderService service, Session session, String orderId) {

        this.service = service;
        this.session = session;
        this.orderId = orderId;
    }

    @Override
    public Set<UserRole> validRoles() {
        return OrdModulePermissions.readyOrderRoles();
    }

    @Override
    public CommandSuccess<ReadyOrderResponse> execute() {
        OrderServiceResult result = service.markAsReadyOrder(String.valueOf(session.getUserID()), session.getRole(), orderId);

        ReadyOrderResponse response = new ReadyOrderResponse(result.getOrderId(), result.getStatus());

        return new CommandSuccess<>(response, "Order marked ready.", result.getEventId());
    }
}