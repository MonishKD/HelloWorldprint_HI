package com.fortunategoods.application.dlv.reassignment;
import java.util.Set;

import com.fortunategoods.application.dlv.ReassignmentService;
import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.courier.DlvModulePermissions;
import com.fortunategoods.domain.user.UserRole;

public class ReassignOrderCommand implements Command<ReassignOrderResponse> {
    private final ReassignmentService service;
    private final Session session;
    private final String orderId;

    public ReassignOrderCommand(ReassignmentService service, Session session, String orderId) {
        this.service = service;
        this.session = session;
        this.orderId = orderId;
    }

    @Override
    public Set<UserRole> validRoles() {
        return DlvModulePermissions.reassignOrderRoles();
    }

    @Override
    public CommandSuccess<ReassignOrderResponse> execute() {
        // Calls the reassignOrder method in service and await result
        ReassignOrderResponse result = service.reassignOrder(String.valueOf(session.getUserID()), session.getRole(), orderId);
        
        // Based on result, choose a message (either goes back to DQ, or assigned to another courier)
        String message = result.getOfferedCourierId() == null ? "Order returned to dispatch queue. Waiting for an idle courier." : "Order offered to courier " + result.getOfferedCourierId() + ".";

        // Return success message
        return CommandSuccess.success(result, message);
    }
}
