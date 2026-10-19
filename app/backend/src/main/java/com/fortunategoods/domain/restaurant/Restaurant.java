package com.fortunategoods.domain.restaurant;

import java.util.List;

import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
public class Restaurant {

    private final String id;
    private final String name;
    private RestaurantStatus status;
    private final double latitude;
    private final double longitude;
    private List<MenuItem> menu;


    public Restaurant(String id, String name, RestaurantStatus status, double latitude, double longitude, List<MenuItem> menu) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.latitude = latitude;
        this.longitude = longitude;
        this.menu = ownItemsOnly(menu);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public RestaurantStatus getStatus() {
        return status;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public List<MenuItem> getMenu() {
        return menu;
    }

    public void setStatus(RestaurantStatus status) {
        this.status = status;
    }

    // Same check as the constructor, so a new menu can't contain another restaurant's items.
    public void setMenu(List<MenuItem> menu) {
        this.menu = ownItemsOnly(menu);
    }

    // Returns a read-only copy of the menu, or throws if an item belongs to another restaurant.
    private List<MenuItem> ownItemsOnly(List<MenuItem> menu) {
        List<MenuItem> copy = List.copyOf(menu);
        for (MenuItem item : copy) {
            if (!item.getRestaurantId().equals(id)) {
                throw new IllegalArgumentException(
                        "menu item " + item.getId() + " belongs to restaurant " + item.getRestaurantId() + ", not " + id);
            }
        }
        return copy;
    }

    public void changeStatus(String staffRestaurantId, RestaurantStatus newStatus) {
        if (!id.equals(staffRestaurantId)) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN,"You do not have permissions to change this restaurant's status.");
        }

        if (newStatus == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Restaurant's new status cannot be empty");
        }

        if (newStatus == RestaurantStatus.OPEN && menu.isEmpty()) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Add at least one menu item before opening");
        }

        this.status = newStatus;
    }
}
