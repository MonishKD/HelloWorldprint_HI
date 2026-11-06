package com.fortunategoods.application.dlv.reassignment;
import java.util.List;
import java.util.Set;

import com.fortunategoods.application.dlv.ReassignmentService;
import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.courier.DlvModulePermissions;
import com.fortunategoods.domain.user.UserRole;

public class GetStalledOrdersCommand implements Command<List<StalledOrderResponse>> {
    private final ReassignmentService service;
    private final Session session;

    public GetStalledOrdersCommand(ReassignmentService service, Session session) {
        this.service = service;
        this.session = session;
    }

    @Override
    public Set<UserRole> validRoles() {
        return DlvModulePermissions.viewStalledOrdersRoles();
    }

    @Override
    public CommandSuccess<List<StalledOrderResponse>> execute() {
        List<StalledOrderResponse> result = service.stalledOrders(String.valueOf(session.getUserID()), session.getRole());
        return CommandSuccess.success(result, result.size() + " stalled orders");
    }
}
