package com.fortunategoods.application.party;

// The card step for one member's share: how much is held, and the Stripe client secret the card field confirms.
// The payment id stays on the server (party_members), so paying needs no body.
public record PartyCheckoutResponse(long amountCents, String clientSecret) {}
