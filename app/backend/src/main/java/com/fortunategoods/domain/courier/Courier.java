package com.fortunategoods.domain.courier;

import com.fortunategoods.domain.order.DomainRuleViolation;

public class Courier {
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

    public void goOnDuty(){
        //check if on duty first
        if (status != CourierStatus.OFFLINE){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Courier is already on duty!");
        }

       //then check if courier has set their location 
        if (latitude == null || longitude == null){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Courier should set their location before going on duty");
        }
        status = CourierStatus.IDLE;
    }

    public void goOffDuty(){
        if (status == CourierStatus.CARRYING){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT,"Courier cannot go off duty while carrying or delivering an order");
        }
        if (status == CourierStatus.OFFLINE){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Courier is already offline/off duty");
        }
        status = CourierStatus.OFFLINE;
    }
}
