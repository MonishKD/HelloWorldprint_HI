package com.fortunategoods.application.dashboard;

import java.util.Set;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.user.UserRole;

/*
    Command for a customer to read their own orders and statuses. DashboardService is the receiver.
    Run it through CommandInvoker.process(command, session) so the session and role are checked first.
*/
public final class GetCustomerDashboardCommand implements Command<CustomerOrderStatusView> {

    private final DashboardService service;
    private final Session session;

    public GetCustomerDashboardCommand(DashboardService service, Session session) {
        this.service = service;
        this.session = session;
    }

    @Override
    public Set<UserRole> validRoles() {
        return OrdModulePermissions.viewOwnOrdersRoles();
    }

    @Override
    public CommandSuccess<CustomerOrderStatusView> execute() {
        CustomerOrderStatusView view = service.customerDashboard(String.valueOf(session.getUserID()), session.getRole());
        return CommandSuccess.success(view, view.orders().size() + " orders loaded");
    }
}
