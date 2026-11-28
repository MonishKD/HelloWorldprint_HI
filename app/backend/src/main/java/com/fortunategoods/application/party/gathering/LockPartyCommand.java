package com.fortunategoods.application.party.gathering;

import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.party.PartyCommand;
import com.fortunategoods.application.party.PartyResponse;
import com.fortunategoods.application.party.PartyService;
import com.fortunategoods.application.reg.Session;

// The host locks the order once everyone is ready: the roulette spins if everyone voted for it, and the shares are fixed.
public class LockPartyCommand extends PartyCommand<PartyResponse> {
    private final String code;

    public LockPartyCommand(PartyService service, Session session, String code) {
        super(service, session);
        this.code = code;
    }

    @Override
    public CommandSuccess<PartyResponse> execute() {
        PartyResponse party = service.lock(customerId(), code);
        String how = "ROULETTE".equals(party.mode()) ? "the roulette picked who pays everything" : "each member pays their own items";
        return CommandSuccess.success(party, "Party " + party.code() + " locked: " + how + ".");
    }
}
