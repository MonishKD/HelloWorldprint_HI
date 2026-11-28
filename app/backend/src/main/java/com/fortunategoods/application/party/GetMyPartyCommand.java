package com.fortunategoods.application.party;

import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;

// The party the customer is in right now (data is null when none). Read on every visit, so not logged.
public class GetMyPartyCommand extends PartyCommand<PartyResponse> {

    public GetMyPartyCommand(PartyService service, Session session) {
        super(service, session);
    }

    @Override
    public boolean logsSuccess() {
        return false;
    }

    @Override
    public CommandSuccess<PartyResponse> execute() {
        return CommandSuccess.success(service.getMyParty(customerId()), "Your party loaded");
    }
}
