package com.fortunategoods.domain.courier;

public class Courier {
    //PLACEHOLDER COURIER CLASS FOR ISSUE #235, TO BE IMPLEMENTED LATER FOR #245
    private String courierID;
    private String name;
    private Double latitude; //Double not double since position can be null too!
    private Double longitude;
    private CourierStatus status;

    public Courier(String courierID, String name, Double latitude, Double longitude, CourierStatus status) {
        this.courierID = courierID;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = status;
    }

    public String getCourierID() {
        return courierID;
    }

    public String getName() {
        return name;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public CourierStatus getStatus(){
        return status;
    }
}
