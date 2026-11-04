package com.fortunategoods.domain.courier;

/*
    Class that will hold the courier ID and it's current coordinates for the
    nearestCourierRule
*/
public class PotentialCourier {
    private final String id;
    private final double latitude;
    private final double longitude;

    public PotentialCourier(String id, double latitude, double longitude){
        this.id = id;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getId() {
        return id;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }
}
