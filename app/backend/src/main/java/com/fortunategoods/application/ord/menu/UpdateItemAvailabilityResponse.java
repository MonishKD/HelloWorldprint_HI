package com.fortunategoods.application.ord.menu;

import com.fortunategoods.domain.restaurant.MenuItemStatus;
//this is what will be returned to the frontend
public class UpdateItemAvailabilityResponse {

    private String itemId;
    private String name;
    private MenuItemStatus status;

    public UpdateItemAvailabilityResponse(String itemId, String name, MenuItemStatus status){
        this.itemId = itemId;
        this.name = name;
        this.status = status;
    }

    public String getItemId(){
        return itemId;
    }

    public String getName(){
        return name;
    }

    public MenuItemStatus getStatus(){
        return status;
    }
    
}
