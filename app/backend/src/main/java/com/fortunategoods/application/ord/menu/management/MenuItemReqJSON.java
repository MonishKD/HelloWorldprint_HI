package com.fortunategoods.application.ord.menu.management;

import com.fortunategoods.domain.restaurant.MenuItemStatus;

/*
    The body the frontend sends to add or edit a menu item:
      POST  /api/restaurants/{restaurantId}/menu
      PATCH /api/restaurants/{restaurantId}/menu/{itemId}
    Example: { "name": "Veggie Wrap", "description": "Fresh", "price": 4.5, "originalPrice": 9, "status": "available" }
*/
public class MenuItemReqJSON {
    private String name;
    private String description;
    private Double price;
    private Double originalPrice; // only for discounted items
    private String status;        // "available" or "sold_out", the same words the frontend uses

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public Double getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(Double originalPrice) { this.originalPrice = originalPrice; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    // Turns the body into a MenuItemInput. Nothing is refused here: MenuService does it,
    // so the refusal goes through CommandInvoker and is written in the command log like every other refusal.
    public MenuItemInput toInput() {
        return new MenuItemInput(name, description, price, originalPrice, toStatus());
    }

    private MenuItemStatus toStatus() {
        if ("available".equalsIgnoreCase(status)) {
            return MenuItemStatus.AVAILABLE;
        }
        if ("sold_out".equalsIgnoreCase(status)) {
            return MenuItemStatus.SOLD_OUT;
        }
        return null;
    }
}
