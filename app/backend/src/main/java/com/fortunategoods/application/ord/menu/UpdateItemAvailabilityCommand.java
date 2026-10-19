package com.fortunategoods.application.ord.menu;

import java.util.Set;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.application.reg.Session;

public class UpdateItemAvailabilityCommand implements Command<Void>{

    private MenuService service;
    private Session session;
    private String itemId;
    private MenuItemStatus newStatus;

    public UpdateItemAvailabilityCommand(MenuService service, Session session, String itemId, MenuItemStatus status){
        this.service = service;
        this.session = session;
        this.itemId = itemId;
        this.newStatus = status;
    }

    //will be called during execution to make sure person doing request is in valid roles
    @Override 
    public Set<UserRole> validRoles(){
        return OrdModulePermissions.updateItemAvailabilityRoles();
    } 
    //calls menu service to change the availability, makes sure the ID is a string since thats the signature required for the User Repo
    @Override 
    public CommandSuccess<Void> execute(){
        service.updateItemAvailability(String.valueOf(session.getUserID()), session.getRole(), itemId, newStatus);
        //returns success messsage, no data needed to pass
        return CommandSuccess.success(null, "Item availability updated");
    }
    
}
