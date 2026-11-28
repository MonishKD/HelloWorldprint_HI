package com.fortunategoods.domain.party;

// One person in a party, as saved in party_members. name is their customer name (or username).
// shareCents is what they pay, saved when the host locks; paymentId is their own card hold (Stripe), set at checkout.
public record PartyMember(String customerId, String name, boolean ready, PartyVote vote, long shareCents,
        String paymentId, boolean paid) {}
