package com.fortunategoods.domain.order;
import com.fortunategoods.domain.restaurant.menu.MenuItem;

public class OrderLine {

    private final String id;
    private final String menuItemId;
    private final MenuItem menuItem;
    private final int quantity;
    private final String name;
    private final double price;
    private final Double originalPrice;

    public OrderLine(String id, String menuItemId, int quantity, String name, double price, Double originalPrice, MenuItem menuItem) {
        // Checks for when repository loads data in
        if (quantity <= 0){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Quantity must be greater 0");
        }

        if (menuItem == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Menu Item is mandatory in an order line");
        }

        if (menuItemId == null || !menuItemId.equals(menuItem.getId())){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Menu Item ID does not match menu item");
        }

        if (name == null || name.isBlank()){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Name is mandatory in an order line");
        }

        if (!Double.isFinite(price) || price < 0) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Price must be non-negative");
        }

        if (originalPrice != null && (!Double.isFinite(originalPrice) || originalPrice < 0)) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Original price must be a non-negative");
        }
        
        this.id = id;
        this.menuItemId = menuItemId;
        this.menuItem = menuItem;
        this.quantity = quantity;
        this.name = name;
        this.price = price;
        this.originalPrice = originalPrice;
    }

    public static OrderLine fromMenuItem(MenuItem menuItem, int quantity){
        if (menuItem == null){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "An order line must have a menu item");
        }
        return new OrderLine(null, menuItem.getId(), quantity, menuItem.getName(), menuItem.getPrice(), menuItem.getOriginalPrice(), menuItem);
    }

    public String getId() {
        return id;
    }

    public String getMenuItemId() {
        return menuItemId;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    public Double getOriginalPrice() {
        return originalPrice;
    }

    public MenuItem getMenuItem() {
        return menuItem;
    }
}
