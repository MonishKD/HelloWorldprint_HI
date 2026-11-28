package com.fortunategoods.domain.party;

// Where a party is (phase4 party mode):
//   GATHERING: people join, add their own items and say they are ready
//   PAYING:    the host locked it, each member pays their share
//   PLACED:    everyone paid, the order went out under the host's name
//   CANCELLED: the host cancelled it, or the order could not be placed
public enum PartyStatus {
    GATHERING,
    PAYING,
    PLACED,
    CANCELLED
}
