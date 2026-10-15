package com.fortunategoods.domain.restaurant;

import java.util.List;
public class Restaurant {
    private String id;
    private String name;
    private RestaurantStatus status;
    private double latitude;
    private double longitude;
    private List<MenuItem> menu;


    public Restaurant(String id, String name, RestaurantStatus status, double latitude, double longitude, List<MenuItem> menu) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.latitude = latitude;
        this.longitude = longitude;
        this.menu = menu;
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
