package com.fortunategoods.application.ord.menu;

import java.util.Set;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.user.UserRole;

/*
    Command for a restaurant that adds an item to its own menu. MenuService is the receiver.
    Run it through CommandInvoker.process(command, session) so the session and role are checked first.
    The staff member's id and role come from the Session, never from the request.
*/
public final class AddMenuItemCommand implements Command<MenuItemResponse> {

    private final MenuService service;
    private final Session session;
    private final String restaurantId;   // from the URL, MenuService checks it is the staff member's own
    private final MenuItemInput input;

    public AddMenuItemCommand(MenuService service, Session session, String restaurantId, MenuItemInput input) {
        this.service = service;
        this.session = session;
        this.restaurantId = restaurantId;
        this.input = input;
    }

    @Override
    public Set<UserRole> validRoles() {
        return OrdModulePermissions.addMenuItemRoles();
    }

    @Override
    public CommandSuccess<MenuItemResponse> execute() {
        MenuItemResponse item = service.addMenuItem(String.valueOf(session.getUserID()), session.getRole(), restaurantId, input);
        return CommandSuccess.success(item, "Menu item \"" + item.name() + "\" added");
    }
}
