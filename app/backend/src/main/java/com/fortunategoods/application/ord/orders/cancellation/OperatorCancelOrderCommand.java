package com.fortunategoods.application.ord.orders.cancellation;

import com.fortunategoods.application.ord.orders.OrderStatusResponse;
import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.ord.orders.OrderService;
import com.fortunategoods.application.ord.orders.OrderServiceResult;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.user.UserRole;
import java.util.Set;

/*
    Command for canceling an order as operator
*/
public class OperatorCancelOrderCommand implements Command<OrderStatusResponse> {
    private final OrderService service;
    private final Session session;
    private final String orderId;

    public OperatorCancelOrderCommand(OrderService service, Session session, String orderId){
        this.service = service;
        this.session = session;
        this.orderId = orderId;
    }

    @Override
    public Set<UserRole> validRoles(){
        return OrdModulePermissions.operatorCancelOrderRoles();
    }

    @Override
    public CommandSuccess<OrderStatusResponse> execute(){
        // Execute command
        OrderServiceResult result = service.operatorCancelOrder(String.valueOf(session.getUserID()), session.getRole(), orderId);

        // Build response to send to frontend
        OrderStatusResponse response = new OrderStatusResponse(result.getOrderId(), result.getStatus());

        // Send response to frontend
        return new CommandSuccess<OrderStatusResponse>(response, "Order cancelled successfully by operator", result.getEventId());
    }

}
