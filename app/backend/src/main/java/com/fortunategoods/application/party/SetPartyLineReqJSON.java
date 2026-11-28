package com.fortunategoods.application.party;

// The body of POST /api/parties/{code}/lines: how many of this menu item I want (0 removes it).
// Integer (not int) so a missing quantity arrives as null and is refused.
public record SetPartyLineReqJSON(String itemId, Integer quantity) {}
