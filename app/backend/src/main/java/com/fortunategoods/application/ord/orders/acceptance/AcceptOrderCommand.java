package com.fortunategoods.application.ord.orders.acceptance;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.ord.orders.OrderService;
import com.fortunategoods.application.ord.orders.OrderServiceResult;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.user.UserRole;
import java.util.Set;


public class AcceptOrderCommand implements Command<AcceptOrderResponse> {
    private final OrderService service;
    private final Session session;
    private final String orderId;

    public AcceptOrderCommand(OrderService service, Session session, String orderId){
        this.service = service;
        this.session = session;
        this.orderId = orderId;
    }

    @Override
    public Set<UserRole> validRoles(){
        return OrdModulePermissions.acceptOrderRoles();
    }

    @Override
    public CommandSuccess<AcceptOrderResponse> execute(){
        // Execute command
        OrderServiceResult result = service.acceptOrder(String.valueOf(session.getUserID()), session.getRole(), orderId);

        // Build response to send to frontend
        AcceptOrderResponse response = new AcceptOrderResponse(result.getOrderId(), result.getStatus());

        return new CommandSuccess<>(response, "Order accepted.", result.getEventId());
    }
}
