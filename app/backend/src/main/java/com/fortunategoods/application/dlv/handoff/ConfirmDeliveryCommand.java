package com.fortunategoods.application.dlv.handoff;
import java.util.Set;

import com.fortunategoods.application.dlv.DeliveryService;
import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.ord.orders.OrderServiceResult;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.courier.DlvModulePermissions;
import com.fortunategoods.domain.user.UserRole;

public class ConfirmDeliveryCommand implements Command<HandoffResponse>{
    private final DeliveryService service;
    private final Session session;

    public ConfirmDeliveryCommand(DeliveryService service, Session session) {
        this.service = service;
        this.session = session;
    }

    @Override
    public Set<UserRole> validRoles() {
        return DlvModulePermissions.confirmDeliveryRoles();
    }

    @Override
    public CommandSuccess<HandoffResponse> execute() {
        // Confirm delivery and await result
        OrderServiceResult result = service.confirmDelivery(String.valueOf(session.getUserID()), session.getRole());

        // Build response based off result
        HandoffResponse response = new HandoffResponse(result.getOrderId(), result.getStatus());

        // Return response
        return new CommandSuccess<>(response, "Order delivered.", result.getEventId());
    }
}
