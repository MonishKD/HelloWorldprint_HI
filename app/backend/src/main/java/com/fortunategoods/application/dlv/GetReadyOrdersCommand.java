package com.fortunategoods.application.dlv;

import java.util.List;
import java.util.Set;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.courier.DlvModulePermissions;
import com.fortunategoods.domain.courier.ReadyOrderSummary;
import com.fortunategoods.domain.user.UserRole;

public final class GetReadyOrdersCommand implements Command<List<ReadyOrderSummary>> {

    private final CourierDutyService service;
    private final Session session;

    public GetReadyOrdersCommand(CourierDutyService service, Session session) {
        this.service = service;
        this.session = session;
    }

    @Override
    public Set<UserRole> validRoles() {
        return DlvModulePermissions.viewReadyOrdersRoles();
    }

    @Override
    public CommandSuccess<List<ReadyOrderSummary>> execute() {
        List<ReadyOrderSummary> readyOrders = service.readyOrders(session.getRole());
        return CommandSuccess.success(readyOrders, readyOrders.size() + " ready orders have been retrieved successfully!");
    }

    @Override
    public boolean logsSuccess() {
        return false; //just a read-only command
    }
}