package com.fortunategoods.domain.restaurant;

import java.util.List;
public class Restaurant {
    // set as final to make variales immutable, and to ensure that they are initialized in the constructor
    private final String id;
    private final String name;
    private final RestaurantStatus status;
    private final double latitude;
    private final double longitude;
    private final List<MenuItem> menu;


    public Restaurant(String id, String name, RestaurantStatus status, double latitude, double longitude, List<MenuItem> menu) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.latitude = latitude;
        this.longitude = longitude;
        this.menu = List.copyOf(menu);
        for (MenuItem item : this.menu) {
            if (!item.getRestaurantId().equals(id)) {
                throw new IllegalArgumentException(
                        "menu item " + item.getId() + " belongs to restaurant " + item.getRestaurantId() + ", not " + id);
            }
        }
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
    
}
