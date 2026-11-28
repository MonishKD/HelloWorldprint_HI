package com.fortunategoods.domain.party;

// How a member wants to pay, chosen when they say they are ready. It is also the party's mode once the host locks:
//   ORDINARY: each person pays for the items they added, plus an even part of the fees
//   ROULETTE: one random member pays the whole order (only when every member voted for it)
public enum PartyVote {
    ORDINARY,
    ROULETTE
}
