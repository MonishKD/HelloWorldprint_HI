package com.fortunategoods.application.party;

import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;

// Reads a party by its code. The party room polls it every 2 seconds, so it is not logged in the console.
public class GetPartyCommand extends PartyCommand<PartyResponse> {
    private final String code;

    public GetPartyCommand(PartyService service, Session session, String code) {
        super(service, session);
        this.code = code;
    }

    @Override
    public boolean logsSuccess() {
        return false;
    }

    @Override
    public CommandSuccess<PartyResponse> execute() {
        return CommandSuccess.success(service.getParty(code), "Party loaded");
    }
}
