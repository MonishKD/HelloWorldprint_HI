package com.fortunategoods.domain.courier;

import java.util.Set;

import com.fortunategoods.domain.user.UserRole;

// User roles allowed to run each DLV command.
public class DlvModulePermissions {
    private DlvModulePermissions() {}

    public static Set<UserRole> viewDispatchQueueRoles() {
        return Set.of(UserRole.OPERATOR);
    }
}
