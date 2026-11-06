package com.fortunategoods.domain.user;

// A customer's saved address and its coordinates (#233). Placement copies it onto the order as the delivery destination.
public class CustomerLocation {
    private final String address;
    private final double latitude;
    private final double longitude;

    public CustomerLocation(String address, double latitude, double longitude) {
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getAddress() {
        return address;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }
}
