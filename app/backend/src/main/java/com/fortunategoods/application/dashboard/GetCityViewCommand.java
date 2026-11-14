package com.fortunategoods.application.dashboard;

import java.util.Set;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.user.UserRole;

public final class GetCityViewCommand implements Command<CityView> {

    private final DashboardService service;
    private final Session session;

    public GetCityViewCommand(DashboardService service, Session session) {
        this.service = service;
        this.session = session;
    }

    @Override
    public Set<UserRole> validRoles() {
        return OrdModulePermissions.viewCityRoles();
    }

    @Override
    public CommandSuccess<CityView> execute() {
        CityView view = service.cityView(session.getRole());
        return CommandSuccess.success(view, view.restaurants().size() + " restaurants and " + view.couriers().size() + " couriers on duty");
    }

    @Override
    public boolean logsSuccess() {
        return false;
    }
}
