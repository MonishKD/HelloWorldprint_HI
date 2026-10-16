package com.fortunategoods.domain.order;

public class OrderLine {

    private String id;
    private String menuItemId;
    private int quantity;
    private String name;
    private double price;
    private Double originalPrice;

    public OrderLine(String id, String menuItemId, int quantity, String name, double price, Double originalPrice) {
        this.id = id;
        this.menuItemId = menuItemId;
        this.quantity = quantity;
        this.name = name;
        this.price = price;
        this.originalPrice = originalPrice;
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

}
