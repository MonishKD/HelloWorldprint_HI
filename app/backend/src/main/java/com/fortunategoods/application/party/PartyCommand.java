package com.fortunategoods.application.party;

import java.util.Set;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.party.PartyModulePermissions;
import com.fortunategoods.domain.user.UserRole;

// What every party command shares: PartyService is the receiver, only customers may run it,
// and who acts comes from the session. Each action is its own small class below (gathering/, payment/).
public abstract class PartyCommand<T> implements Command<T> {

    protected final PartyService service;
    private final Session session;

    protected PartyCommand(PartyService service, Session session) {
        this.service = service;
        this.session = session;
    }

    protected String customerId() {
        return String.valueOf(session.getUserID());
    }

    @Override
    public Set<UserRole> validRoles() {
        return PartyModulePermissions.partyRoles();
    }
}
