package com.fortunategoods.application.ord.orders.cancellation;

import com.fortunategoods.application.ord.orders.OrderStatusResponse;
import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.ord.orders.OrderService;
import java.util.Set;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.application.ord.orders.OrderServiceResult;
import com.fortunategoods.domain.user.UserRole;

public class CancelOrderCommand implements Command<OrderStatusResponse> {
    private final OrderService service;
    private final Session session;
    private final String orderId;

    public CancelOrderCommand(OrderService service, Session session, String orderId){
        this.service = service;
        this.session = session;
        this.orderId = orderId;
    }

    // Valid roles for cancelOrder()
    @Override
    public Set<UserRole> validRoles(){
        return OrdModulePermissions.cancelOrderRoles();
    }

    @Override
    public CommandSuccess<OrderStatusResponse> execute(){
        // call the cancelOrder() method in OrderService
        OrderServiceResult result = service.cancelOrder(String.valueOf(session.getUserID()), orderId);

        // create the response to send back to frontend
        OrderStatusResponse response = new OrderStatusResponse(result.getOrderId(), result.getStatus());

        // send response
        return new CommandSuccess<>(response, "Order cancelled.", result.getEventId());
    }
}
