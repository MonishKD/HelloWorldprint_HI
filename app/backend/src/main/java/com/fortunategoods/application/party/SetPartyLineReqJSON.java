package com.fortunategoods.application.party;

import java.math.BigDecimal;

// The body of POST /api/parties/{code}/lines: how many of this menu item I want (0 removes it).
// BigDecimal (not int) so a missing quantity arrives as null and 1.5 is refused instead of quietly becoming 1.
public record SetPartyLineReqJSON(String itemId, BigDecimal quantity) {}
