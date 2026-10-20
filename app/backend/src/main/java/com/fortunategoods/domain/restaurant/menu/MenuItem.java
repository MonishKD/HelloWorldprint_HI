package com.fortunategoods.domain.restaurant.menu;

import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import java.util.Objects;

// One item on a restaurant's menu. Most fields are final, so they cannot change. 
// MenuItem's availability status is the only field that can change
public final class MenuItem {

    private final String id;
    private final String restaurantId; // the restaurant this item belongs to
    private final String name;
    private MenuItemStatus status; // not final since status can change
    private final double price;
    private final Double originalPrice; // Double wrapper to save it in-memory

    // Existing code will use this constructor until appropriate changes are applied, to use the other constructor
    public MenuItem(String id, String restaurantId, String name, MenuItemStatus status, double price){
        this.id = requireNonBlank(id, "id");
        this.restaurantId = requireNonBlank(restaurantId, "restaurantId");
        this.name = requireNonBlank(name, "name");
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (!Double.isFinite(price) || price < 0) {
            throw new IllegalArgumentException("price must be a non-negative number");
        }
        this.price = price;
        this.originalPrice = null;
    }

    // Refuses bad data: blank id / restaurantId / name, missing status, negative or NaN price.
    public MenuItem(String id, String restaurantId, String name, MenuItemStatus status, double price, Double originalPrice) {
        this.id = requireNonBlank(id, "id");
        this.restaurantId = requireNonBlank(restaurantId, "restaurantId");
        this.name = requireNonBlank(name, "name");
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (!Double.isFinite(price) || price < 0) {
            throw new IllegalArgumentException("price must be a non-negative number");
        }
        if (originalPrice != null && (!Double.isFinite(originalPrice) || originalPrice < 0)){
            throw new IllegalArgumentException("originalPrice must be non-negative");
        }
        this.price = price;
        this.originalPrice = originalPrice;
    }

    public String getId() {
        return id;
    }

    public String getRestaurantId() {
        return restaurantId;
    }

    public String getName() {
        return name;
    }

    public MenuItemStatus getStatus() {
        return status;
    }

    public double getPrice() {
        return price;
    }

    public Double getOriginalPrice(){
        return originalPrice;
    }

    // true when the item can be ordered
    public boolean isAvailable() {
        return status == MenuItemStatus.AVAILABLE;
    }

    // true when the item is sold out
    public boolean isSoldOut() {
        return status == MenuItemStatus.SOLD_OUT;
    }

    // Returns the value, or throws if it is null or only spaces.
    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    // Setter for status
    public void setStatus(MenuItemStatus status) {
        this.status = Objects.requireNonNull(status, "Status can not be null");
    }

    //change only if restaurant matches
    public void changeAvailability(String staffRestoId, MenuItemStatus newStatus){
        if (!restaurantId.equals(staffRestoId)){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN, "restaurant staff can only change their own menu items");
        }
        MenuItemRules.requireAvailability(newStatus);
        setStatus(newStatus);
    }

}
