package com.fortunategoods.application.party.gathering;

import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.party.PartyCommand;
import com.fortunategoods.application.party.PartyResponse;
import com.fortunategoods.application.party.PartyService;
import com.fortunategoods.application.reg.Session;

// A member says they are ready and votes ORDINARY or ROULETTE, or says they are not ready anymore.
public class SetReadyCommand extends PartyCommand<PartyResponse> {
    private final String code;
    private final Boolean ready;
    private final String vote;

    public SetReadyCommand(PartyService service, Session session, String code, Boolean ready, String vote) {
        super(service, session);
        this.code = code;
        this.ready = ready;
        this.vote = vote;
    }

    @Override
    public CommandSuccess<PartyResponse> execute() {
        PartyResponse party = service.setReady(customerId(), code, ready, vote);
        String what = Boolean.TRUE.equals(ready) ? "Ready, voted " + vote : "Not ready anymore";
        return CommandSuccess.success(party, what + " in party " + party.code() + ".");
    }
}
