package com.fortunategoods.domain.courier;

import java.util.Set;

import com.fortunategoods.domain.user.UserRole;

// User roles allowed to run each DLV command.
public class DlvModulePermissions {
    private DlvModulePermissions() {}

    public static Set<UserRole> viewDispatchQueueRoles() {
        return Set.of(UserRole.OPERATOR);
    }

    public static Set<UserRole> confirmPickupRoles() {
        return Set.of(UserRole.COURIER);
    }

    public static Set<UserRole> confirmDeliveryRoles() {
        return Set.of(UserRole.COURIER);
    }

    public static Set<UserRole> reassignOrderRoles() {
        return Set.of(UserRole.OPERATOR);
    }

    public static Set<UserRole> viewStalledOrdersRoles() {
        return Set.of(UserRole.OPERATOR);
    }

    public static Set<UserRole> goOnDutyRoles() {
        return Set.of(UserRole.COURIER);
    }

    public static Set<UserRole> goOffDutyRoles() {
        return Set.of(UserRole.COURIER);
    }

    //these two are for the /me and /ready-orders reads!
    public static Set<UserRole> viewOwnCourierStateRoles() {
        return Set.of(UserRole.COURIER);
    }

    public static Set<UserRole> viewReadyOrdersRoles() {
        return Set.of(UserRole.COURIER);
    }
}
