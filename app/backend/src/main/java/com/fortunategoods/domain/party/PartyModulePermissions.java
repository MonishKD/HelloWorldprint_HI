package com.fortunategoods.domain.party;

import java.util.Set;

import com.fortunategoods.domain.user.UserRole;

// Which roles may run each party command, like OrdModulePermissions for ORD.
public class PartyModulePermissions {
    private PartyModulePermissions() {} // No instances will be created

    // Only customers order food, so only customers host, join and pay in a party.
    public static Set<UserRole> partyRoles() {
        return Set.of(UserRole.CUSTOMER);
    }
}
