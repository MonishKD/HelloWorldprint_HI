package com.fortunategoods.application.dashboard;

import java.util.List;
import java.util.Set;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.user.UserRole;

/*
    Command for the operator to read every order (#274). DashboardService is the receiver.
    Run it through CommandInvoker.process(command, session) so the session and role are checked first.
*/
public class GetAllOrdersCommand implements Command<List<OperatorOrderResponse>> {
    private final DashboardService service;
    private final Session session;

    public GetAllOrdersCommand(DashboardService service, Session session) {
        this.service = service;
        this.session = session;
    }

    @Override
    public Set<UserRole> validRoles() {
        return OrdModulePermissions.viewAllOrdersRoles();
    }

    @Override
    public CommandSuccess<List<OperatorOrderResponse>> execute() {
        List<OperatorOrderResponse> orders = service.allOrders(String.valueOf(session.getUserID()), session.getRole());

        // Only a read: no state changes, so no eventId and no OrderEvent
        return CommandSuccess.success(orders, orders.size() + " orders loaded");
    }
}
