package com.fortunategoods.application.party.gathering;

import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.party.PartyCommand;
import com.fortunategoods.application.party.PartyResponse;
import com.fortunategoods.application.party.PartyService;
import com.fortunategoods.application.reg.Session;

// Cancels the party before its order is placed: the host while gathering, any member while paying.
public class CancelPartyCommand extends PartyCommand<PartyResponse> {
    private final String code;

    public CancelPartyCommand(PartyService service, Session session, String code) {
        super(service, session);
        this.code = code;
    }

    @Override
    public CommandSuccess<PartyResponse> execute() {
        PartyResponse party = service.cancel(customerId(), code);
        return CommandSuccess.success(party, "Party " + party.code() + " cancelled.");
    }
}
