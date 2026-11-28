package com.fortunategoods.application.party.gathering;

import java.math.BigDecimal;

import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.party.PartyCommand;
import com.fortunategoods.application.party.PartyResponse;
import com.fortunategoods.application.party.PartyService;
import com.fortunategoods.application.reg.Session;

// A member sets how many of one menu item they want in the party (0 removes it).
public class SetPartyLineCommand extends PartyCommand<PartyResponse> {
    private final String code;
    private final String itemId;
    private final BigDecimal quantity;

    public SetPartyLineCommand(PartyService service, Session session, String code, String itemId, BigDecimal quantity) {
        super(service, session);
        this.code = code;
        this.itemId = itemId;
        this.quantity = quantity;
    }

    @Override
    public CommandSuccess<PartyResponse> execute() {
        PartyResponse party = service.setLine(customerId(), code, itemId, quantity);
        String what = (quantity != null && quantity.signum() == 0) ? "Item removed" : "Item set to " + quantity.toPlainString();
        return CommandSuccess.success(party, what + " in party " + party.code() + ".");
    }
}
