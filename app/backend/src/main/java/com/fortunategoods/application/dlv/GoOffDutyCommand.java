package com.fortunategoods.application.dlv;


import java.util.Set;
import java.util.List;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.courier.DlvModulePermissions;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.domain.courier.CourierStatus;


public final class GoOffDutyCommand implements Command<CourierStatus> {

    private final CourierDutyService service;
    private final Session session;

    public GoOffDutyCommand(CourierDutyService service, Session session) {
        this.service = service;
        this.session = session;
    }

    @Override
    public Set<UserRole> validRoles() {
        return DlvModulePermissions.goOffDutyRoles();
    }

    @Override
    public CommandSuccess<CourierStatus> execute() {
        service.goOffDuty(String.valueOf(session.getUserID()));
        return CommandSuccess.success(CourierStatus.OFFLINE, "Courier is now off duty!");
    }
    
}
