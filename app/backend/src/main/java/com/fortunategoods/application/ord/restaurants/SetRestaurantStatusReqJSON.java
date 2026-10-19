package com.fortunategoods.application.ord.restaurants;

/*
    Class used for frontend request body
*/
public class SetRestaurantStatusReqJSON {
    private String status;

    public SetRestaurantStatusReqJSON() {
    }

    public SetRestaurantStatusReqJSON(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
