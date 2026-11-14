package com.fortunategoods.application.dlv;


import java.util.Set;
import java.util.List;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.courier.DlvModulePermissions;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.domain.courier.CourierStatus;


public final class GoOnDutyCommand implements Command<CourierStatus> {//changing List to CourierStatus for going on and off duty
    
    private final CourierDutyService service;
    private final Session session;

    public GoOnDutyCommand(CourierDutyService service, Session session) {
        this.service = service;
        this.session = session;
    }

    @Override
    public Set<UserRole> validRoles() {
        return DlvModulePermissions.goOnDutyRoles();
    }

    @Override
    public CommandSuccess<CourierStatus> execute() {
        service.goOnDuty(String.valueOf(session.getUserID()));
        return CommandSuccess.success(CourierStatus.IDLE, "Courier is now on duty!");
    }
    
}
