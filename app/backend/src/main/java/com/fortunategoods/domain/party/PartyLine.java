package com.fortunategoods.domain.party;

// One item a member added to the party, as saved in party_lines. The name comes from the menu.
// lockedPriceCents is the price of one, copied from the menu when the host locked (null before the lock).
public record PartyLine(String memberId, String menuItemId, int quantity, Long lockedPriceCents) {}
