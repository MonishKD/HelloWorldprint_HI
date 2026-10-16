package com.fortunategoods.domain.order;
import java.util.Set;

import com.fortunategoods.domain.user.UserRole;

/*
    Class that returns user roles that are allowed for each concrete commands
*/
public class OrdModulePermissions {
    private OrdModulePermissions(){} // No instances will be created

    public static Set<UserRole> browseMenuRoles(){
        return Set.of(UserRole.CUSTOMER, UserRole.RESTAURANT);
    }

    public static Set<UserRole> placeOrderRoles(){
        return Set.of(UserRole.CUSTOMER);
    }

    public static Set<UserRole> preparingOrderRoles(){
        return Set.of(UserRole.RESTAURANT);
    }

    public static Set<UserRole> cancelOrderRoles(){
        return Set.of(UserRole.CUSTOMER);
    }

    public static Set<UserRole> acceptOrderRoles(){
        return Set.of(UserRole.RESTAURANT);
    }

    public static Set<UserRole> rejectOrderRoles(){
        return Set.of(UserRole.RESTAURANT);
    }

    public static Set<UserRole> readyOrderRoles(){
        return Set.of(UserRole.RESTAURANT);
    }

    public static Set<UserRole> updateItemAvailabilityRoles(){
        return Set.of(UserRole.RESTAURANT);
    }

}
