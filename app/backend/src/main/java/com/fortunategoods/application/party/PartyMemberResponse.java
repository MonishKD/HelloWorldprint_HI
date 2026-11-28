package com.fortunategoods.application.party;

// One person in the party. shareCents is what they pay: a live estimate while gathering, fixed once the host locks.
// vote is ORDINARY, ROULETTE or null (not ready yet).
public record PartyMemberResponse(String id, String name, boolean host, boolean ready, String vote, long shareCents,
        boolean paid) {}
