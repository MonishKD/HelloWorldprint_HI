package com.fortunategoods.domain.restaurant.menu;

import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.restaurant.MenuItemStatus;

/*
    Domain rules for adding and editing menu items. MenuService calls them before anything is saved.
    Every refusal is a DomainRuleViolation with a reason the user can act on:
      INVALID   bad item (missing name, price not above zero...)
      FORBIDDEN not the staff member's own restaurant
*/
public final class MenuItemRules {
    private MenuItemRules() {}

    // Same limits as the menu_items table, so a long text is refused clearly instead of failing in the database.
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    // Their restaurant comes from their account, never from the request.
    public static void requireOwnRestaurant(String staffRestaurantId, String restaurantId) {
        if (staffRestaurantId == null || !staffRestaurantId.equals(restaurantId)) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN, "Restaurant staff can only change their own restaurant's menu");
        }
    }

    public static String cleanName(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Give the item a name");
        }
        String clean = name.trim();
        if (clean.length() > MAX_NAME_LENGTH) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "The name can have at most " + MAX_NAME_LENGTH + " characters");
        }
        return clean;
    }

    public static String cleanDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String clean = description.trim();
        if (clean.length() > MAX_DESCRIPTION_LENGTH) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "The description can have at most " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        return clean;
    }

    public static void requireValidPrices(Double price, Double originalPrice) {
        if (price == null || !Double.isFinite(price) || price <= 0) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "The price must be above zero");
        }
        if (originalPrice != null && (!Double.isFinite(originalPrice) || originalPrice < price)) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "The original price can't be lower than the price");
        }
    }

    public static void requireAvailability(MenuItemStatus status) {
        if (status == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Status must be available or sold_out");
        }
    }

    public static void requireItemOnMenu(MenuItem item, String restaurantId) {
        if (item == null || !item.getRestaurantId().equals(restaurantId)) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "Item not found on this restaurant's menu");
        }
    }

    public static MenuItem checkedItem(String itemId, String restaurantId, String name, Double price, Double originalPrice, MenuItemStatus status) {
        String checkedName = cleanName(name);
        requireValidPrices(price, originalPrice);
        requireAvailability(status);
        return new MenuItem(itemId, restaurantId, checkedName, status, price, originalPrice);
    }
}
