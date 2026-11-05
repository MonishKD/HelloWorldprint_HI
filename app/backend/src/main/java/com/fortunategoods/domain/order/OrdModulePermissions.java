package com.fortunategoods.domain.order;
import java.util.Set;

import com.fortunategoods.domain.user.UserRole;

/*
    Class that returns user roles that are allowed for each concrete commands
*/
public class OrdModulePermissions {
    private OrdModulePermissions(){} // No instances will be created

    public static void requireRole(UserRole role, Set<UserRole> allowedRoles, String message) {
        if (role == null || !allowedRoles.contains(role)) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN, message);
        }
    }

    public static void requireOwnRestaurant(String staffRestaurantId, String restaurantId, String message) {
        if (staffRestaurantId == null || !staffRestaurantId.equals(restaurantId)) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN, message);
        }
    }

    // Story #43: the restaurant list is for signed-in customers.
    public static Set<UserRole> browseRestaurantsRoles(){
        return Set.of(UserRole.CUSTOMER);
    }

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

    public static Set<UserRole> operatorCancelOrderRoles() {
        return Set.of(UserRole.OPERATOR);
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

    public static Set<UserRole> addMenuItemRoles(){
        return Set.of(UserRole.RESTAURANT);
    }

    public static Set<UserRole> editMenuItemRoles(){
        return Set.of(UserRole.RESTAURANT);
    }

    public static Set<UserRole> removeMenuItemRoles(){
        return Set.of(UserRole.RESTAURANT);
    }

    // #274: the operator can pause or reopen any restaurant (R-DASH-01).
    public static Set<UserRole> setRestaurantStatusRoles(){
        return Set.of(UserRole.RESTAURANT, UserRole.OPERATOR);
    }

    public static Set<UserRole> viewPlacedOrdersRoles(){
        return Set.of(UserRole.RESTAURANT);
    }

    public static Set<UserRole> viewPreparationQueueRoles() {
        return Set.of(UserRole.RESTAURANT);
    }

    public static Set<UserRole> viewOwnOrdersRoles() {
        return Set.of(UserRole.CUSTOMER);
    }

    // #274: every order in the system, for the operator dashboard (R-DASH-01).
    public static Set<UserRole> viewAllOrdersRoles() {
        return Set.of(UserRole.OPERATOR);
    }
}
