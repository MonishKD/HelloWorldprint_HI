package com.fortunategoods.application.party.gathering;

import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.party.PartyCommand;
import com.fortunategoods.application.party.PartyResponse;
import com.fortunategoods.application.party.PartyService;
import com.fortunategoods.application.reg.Session;

// A signed-in customer joins with the code (typed, from the share link or from the QR code).
public class JoinPartyCommand extends PartyCommand<PartyResponse> {
    private final String code;

    public JoinPartyCommand(PartyService service, Session session, String code) {
        super(service, session);
        this.code = code;
    }

    @Override
    public CommandSuccess<PartyResponse> execute() {
        PartyResponse party = service.join(customerId(), code);
        return CommandSuccess.success(party, "Joined party " + party.code() + ".");
    }
}
