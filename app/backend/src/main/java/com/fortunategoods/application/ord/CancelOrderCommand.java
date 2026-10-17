package com.fortunategoods.application.ord;

import java.util.Set;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.order.OrderServiceResult;
import com.fortunategoods.domain.user.UserRole;

public class CancelOrderCommand implements Command<CancelOrderResponse> {
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
    public CommandSuccess<CancelOrderResponse> execute(){
        // call the cancelOrder() method in OrderService
        OrderServiceResult result = service.cancelOrder(String.valueOf(session.getUserID()), orderId);

        // create the response to send back to frontend
        CancelOrderResponse response = new CancelOrderResponse(result.getOrderId(), result.getStatus());

        // send response
        return new CommandSuccess<>(response, "Order cancelled.", result.getEventId());
    }
}
