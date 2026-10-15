package com.fortunategoods.domain.restaurant;

public class MenuItem {
    
    private String id;
    private String name;
    private MenuItemStatus status;
    private double price;

    public MenuItem(String id, String name, MenuItemStatus status, double price) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.price = price;
    }

    public String getId() {
        return id;
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

}
