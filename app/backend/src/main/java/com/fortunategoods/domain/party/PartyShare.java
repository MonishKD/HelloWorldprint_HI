package com.fortunategoods.domain.party;

// What one customer paid for one party order, and that party's code. Used to show party orders in their history.
public record PartyShare(String partyCode, long shareCents) {}
