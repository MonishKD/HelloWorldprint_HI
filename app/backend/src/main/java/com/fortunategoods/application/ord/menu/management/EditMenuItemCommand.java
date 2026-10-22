package com.fortunategoods.application.ord.menu.management;

import com.fortunategoods.application.ord.menu.MenuService;

import java.util.Set;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.user.UserRole;

/*
    Command for a restaurant that changes one of its own menu items. MenuService is the receiver.
    Run it through CommandInvoker.process(command, session) so the session and role are checked first.
    The staff member's id and role come from the Session, never from the request.
*/
public final class EditMenuItemCommand implements Command<MenuItemResponse> {

    private final MenuService service;
    private final Session session;
    private final String restaurantId;   // from the URL, MenuService checks it is the staff member's own
    private final String itemId;
    private final MenuItemInput input;

    public EditMenuItemCommand(MenuService service, Session session, String restaurantId, String itemId, MenuItemInput input) {
        this.service = service;
        this.session = session;
        this.restaurantId = restaurantId;
        this.itemId = itemId;
        this.input = input;
    }

    @Override
    public Set<UserRole> validRoles() {
        return OrdModulePermissions.editMenuItemRoles();
    }

    @Override
    public CommandSuccess<MenuItemResponse> execute() {
        MenuItemResponse item = service.editMenuItem(String.valueOf(session.getUserID()), session.getRole(), restaurantId, itemId, input);
        return CommandSuccess.success(item, "Menu item \"" + item.name() + "\" edited");
    }
}
