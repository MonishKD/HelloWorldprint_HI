package com.fortunategoods.domain.restaurant;

import java.util.Objects;

// One item on a restaurant's menu. Every field is final, so an item cannot change after it is created.
public final class MenuItem {

    private final String id;
    private final String restaurantId; // the restaurant this item belongs to
    private final String name;
    private MenuItemStatus status;
    private final double price;

    // Refuses bad data: blank id / restaurantId / name, missing status, negative or NaN price.
    public MenuItem(String id, String restaurantId, String name, MenuItemStatus status, double price) {
        this.id = requireNonBlank(id, "id");
        this.restaurantId = requireNonBlank(restaurantId, "restaurantId");
        this.name = requireNonBlank(name, "name");
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (!Double.isFinite(price) || price < 0) {
            throw new IllegalArgumentException("price must be a non-negative number");
        }
        this.price = price;
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

    public void setStatus(MenuItemStatus status) {
        this.status = status;
    }

}
