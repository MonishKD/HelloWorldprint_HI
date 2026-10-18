package com.fortunategoods.application.ord.orders.rejection;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.ord.orders.OrderServiceResult;
import com.fortunategoods.application.ord.orders.OrderService;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.user.UserRole;
import java.util.Set;

public class RejectOrderCommand implements Command<RejectOrderResponse> {
    private final OrderService service;
    private final Session session;
    private final String orderId;
    private final String reason;

    public RejectOrderCommand(OrderService service, Session session, String orderId, String reason){
        this.service = service;
        this.session = session;
        this.orderId = orderId;
        this.reason = reason;
    }

    @Override
    public Set<UserRole> validRoles(){
        return OrdModulePermissions.rejectOrderRoles();
    }

    @Override
    public CommandSuccess<RejectOrderResponse> execute(){
        // Execute command
        OrderServiceResult result = service.rejectOrder(String.valueOf(session.getUserID()), session.getRole(), orderId, reason);

        // Build response to send to frontend
        RejectOrderResponse response = new RejectOrderResponse(result.getOrderId(), result.getStatus(), result.getRejectReason());

        // Return successful response if succeeded
        return new CommandSuccess<>(response, "Order rejected", result.getEventId());
    }


}
