package com.fortunategoods.application.party.payment;

import java.util.Locale;

import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.party.PartyCheckoutResponse;
import com.fortunategoods.application.party.PartyCommand;
import com.fortunategoods.application.party.PartyService;
import com.fortunategoods.application.reg.Session;

// A member starts paying their share: it is held on their card (like the cart's checkout, #259), nothing is taken yet.
public class PartyCheckoutCommand extends PartyCommand<PartyCheckoutResponse> {
    private final String code;

    public PartyCheckoutCommand(PartyService service, Session session, String code) {
        super(service, session);
        this.code = code;
    }

    @Override
    public CommandSuccess<PartyCheckoutResponse> execute() {
        PartyCheckoutResponse checkout = service.checkout(customerId(), code);
        String amount = String.format(Locale.ROOT, "%.2f", checkout.amountCents() / 100.0);
        return CommandSuccess.success(checkout, "Share checkout started: $" + amount + " will be held on the card when it is confirmed.");
    }
}
