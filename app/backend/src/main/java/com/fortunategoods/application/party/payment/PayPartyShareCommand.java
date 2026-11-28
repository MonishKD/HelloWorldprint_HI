package com.fortunategoods.application.party.payment;

import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.party.PartyCommand;
import com.fortunategoods.application.party.PartyResponse;
import com.fortunategoods.application.party.PartyService;
import com.fortunategoods.application.reg.Session;

// A member confirms their share once the card is confirmed. The last one to pay places the order under the host's name.
public class PayPartyShareCommand extends PartyCommand<PartyResponse> {
    private final String code;

    public PayPartyShareCommand(PartyService service, Session session, String code) {
        super(service, session);
        this.code = code;
    }

    @Override
    public CommandSuccess<PartyResponse> execute() {
        PartyResponse party = service.pay(customerId(), code);
        String message = party.orderId() != null
                ? "Everyone paid: order " + party.orderId() + " placed. The card holds are charged on delivery."
                : "Share paid in party " + party.code() + ". Waiting for the others.";
        return CommandSuccess.success(party, message);
    }
}
