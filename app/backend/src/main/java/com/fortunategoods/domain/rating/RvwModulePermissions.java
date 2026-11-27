package com.fortunategoods.domain.rating;

import java.util.Set;

import com.fortunategoods.domain.user.UserRole;

// Which roles may run each RVW command (Appendix A), like OrdModulePermissions for ORD.
public class RvwModulePermissions {
    private RvwModulePermissions() {} // No instances will be created

    // R-RVW-01: only customers rate.
    public static Set<UserRole> rateOrderRoles() {
        return Set.of(UserRole.CUSTOMER);
    }
}
