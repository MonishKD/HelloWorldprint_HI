package com.fortunategoods.application.party.gathering;

import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.party.PartyCommand;
import com.fortunategoods.application.party.PartyResponse;
import com.fortunategoods.application.party.PartyService;
import com.fortunategoods.application.reg.Session;

// A member (not the host) leaves the party while it is still gathering, so they are free to join another one.
public class LeavePartyCommand extends PartyCommand<PartyResponse> {
    private final String code;

    public LeavePartyCommand(PartyService service, Session session, String code) {
        super(service, session);
        this.code = code;
    }

    @Override
    public CommandSuccess<PartyResponse> execute() {
        PartyResponse party = service.leave(customerId(), code);
        return CommandSuccess.success(party, "Left party " + party.code() + ".");
    }
}
