package com.fortunategoods.application.party;

// One item a member added, with its name and price (in cents) for one of it: the menu's price while gathering,
// the price fixed at the lock afterwards. available is false when the item is sold out or no longer on the menu.
public record PartyLineResponse(String memberId, String itemId, String name, long priceCents, int quantity, boolean available) {}
