package com.fortunategoods.application.dlv;

import java.util.Set;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.courier.DlvModulePermissions;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.domain.courier.CourierStatus;

public final class AcceptOfferCommand implements Command<CourierStatus>{

    private final CourierOfferService service;
    private final Session session;
    
    public AcceptOfferCommand(CourierOfferService service, Session session) {
        this.service = service;
        this.session = session;
    }

    @Override
    public Set<UserRole> validRoles() {
        return DlvModulePermissions.acceptOfferRoles();
    }

    @Override
    public CommandSuccess<CourierStatus> execute() {
        service.accept(String.valueOf(session.getUserID()));
        return CommandSuccess.success(CourierStatus.ASSIGNED, "Offer has been accepted!");
    }

}
