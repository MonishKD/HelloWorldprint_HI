package com.fortunategoods.application.dlv;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.domain.courier.CourierDashboardView;
import com.fortunategoods.domain.courier.DlvModulePermissions;
import com.fortunategoods.domain.courier.ReadyOrderSummary;
import com.fortunategoods.domain.user.UserRole;

import com.fortunategoods.application.reg.Session;

import java.util.Set;

public class GetCourierDashboardCommand implements Command<CourierDashboardView>{

    private final CourierDutyService service;
    private final Session session;

    public GetCourierDashboardCommand(CourierDutyService service, Session session) {
        this.service = service;
        this.session = session;
    }

    @Override
    public Set<UserRole> validRoles() {
        return DlvModulePermissions.viewOwnCourierStateRoles();
    }

    @Override
    public CommandSuccess<CourierDashboardView> execute() {
        CourierDashboardView view = service.courierDashboard(session.getRole(), String.valueOf(session.getUserID()));
        return CommandSuccess.success(view, "Courier dashboard has been retrieved!");
    }

    @Override
    public boolean logsSuccess() {
        return false; //just a read-only command again
    }
}
