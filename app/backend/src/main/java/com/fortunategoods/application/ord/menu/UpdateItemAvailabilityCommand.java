package com.fortunategoods.application.ord.menu;

import java.util.Set;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.application.reg.Session;

/*
    updateItemAvailabilityCommand (#89): a restaurant sets one of its own menu items to available or sold out.
    MenuService is the receiver. Run it through CommandInvoker.process(command, session) so the session and role are checked first.
    The staff member's id and role come from the Session, never from the request.
*/
// Command<UpdateItemAvailabilityResponse>: this command's result (its data) is the updated item.
public class UpdateItemAvailabilityCommand implements Command<UpdateItemAvailabilityResponse>{

    private MenuService service;
    private Session session;
    private String restaurantId; // from the URL, MenuService checks the item really is on this restaurant's menu
    private String itemId;
    private MenuItemStatus newStatus;

    public UpdateItemAvailabilityCommand(MenuService service, Session session, String restaurantId, String itemId, MenuItemStatus status){
        this.service = service;
        this.session = session;
        this.restaurantId = restaurantId;
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
    public CommandSuccess<UpdateItemAvailabilityResponse> execute(){
        MenuItem item = service.updateItemAvailability(String.valueOf(session.getUserID()), session.getRole(), restaurantId, itemId, newStatus);

        //returns the updated item so the frontend can show its new status

        UpdateItemAvailabilityResponse response = new UpdateItemAvailabilityResponse(item.getId(), item.getName(), item.getStatus());

        return CommandSuccess.success(response, "Item availability updated");
    }

}
