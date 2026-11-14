package com.fortunategoods.application.prc.refund;

import java.util.Set;

import com.fortunategoods.application.ord.orders.OrderStatusResponse;
import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.ord.orders.OrderServiceResult;
import com.fortunategoods.application.prc.PaymentService;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.user.UserRole;

public final class RefundOrderCommand implements Command<OrderStatusResponse> {
    private final PaymentService service;
    private final Session session;
    private final String orderId;

    public RefundOrderCommand(PaymentService service, Session session, String orderId) {
        this.service = service;
        this.session = session;
        this.orderId = orderId;
    }

    @Override
    public Set<UserRole> validRoles() {
        return OrdModulePermissions.refundOrderRoles();
    }

    @Override
    public CommandSuccess<OrderStatusResponse> execute() {
        OrderServiceResult result = service.refundOrder(session.getRole(), orderId);
        OrderStatusResponse response = new OrderStatusResponse(result.getOrderId(), result.getStatus());
        return new CommandSuccess<>(response, "Order " + orderId + " refunded", result.getEventId());
    }
}
