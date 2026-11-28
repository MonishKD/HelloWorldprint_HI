package com.fortunategoods.application.party;

// The body of POST /api/parties/{code}/ready: ready or not, and the vote (ORDINARY or ROULETTE) when ready.
public record SetReadyReqJSON(Boolean ready, String vote) {}
