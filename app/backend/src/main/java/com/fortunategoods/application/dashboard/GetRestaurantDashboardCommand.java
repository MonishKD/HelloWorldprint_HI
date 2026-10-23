package com.fortunategoods.application.dashboard;

import java.util.Set;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.user.UserRole;

/*
    Command for restaurant staff to read their own restaurant and its preparation queue. DashboardService is the receiver.
    Run it through CommandInvoker.process(command, session) so the session and role are checked first.
*/
public final class GetRestaurantDashboardCommand implements Command<PreparationQueueView> {

    private final DashboardService service;
    private final Session session;

    public GetRestaurantDashboardCommand(DashboardService service, Session session) {
        this.service = service;
        this.session = session;
    }

    @Override
    public Set<UserRole> validRoles() {
        return OrdModulePermissions.viewPreparationQueueRoles();
    }

    @Override
    public CommandSuccess<PreparationQueueView> execute() {
        PreparationQueueView view = service.restaurantDashboard(String.valueOf(session.getUserID()), session.getRole());
        return CommandSuccess.success(view, view.queue().size() + " orders in the preparation queue");
    }
}
