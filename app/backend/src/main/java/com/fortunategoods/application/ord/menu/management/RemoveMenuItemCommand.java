package com.fortunategoods.application.ord.menu.management;

import com.fortunategoods.application.ord.menu.MenuService;

import java.util.Set;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.user.UserRole;

/*
    Command for a restaurant that removes one of its own menu items. MenuService is the receiver.
    The item is hidden, not deleted, so past orders stay intact.
    Run it through CommandInvoker.process(command, session) so the session and role are checked first.
*/
public final class RemoveMenuItemCommand implements Command<MenuItemResponse> {

    private final MenuService service;
    private final Session session;
    private final String restaurantId;   // from the URL, MenuService checks it is the staff member's own
    private final String itemId;

    public RemoveMenuItemCommand(MenuService service, Session session, String restaurantId, String itemId) {
        this.service = service;
        this.session = session;
        this.restaurantId = restaurantId;
        this.itemId = itemId;
    }

    @Override
    public Set<UserRole> validRoles() {
        return OrdModulePermissions.removeMenuItemRoles();
    }

    @Override
    public CommandSuccess<MenuItemResponse> execute() {
        MenuItemResponse item = service.removeMenuItem(String.valueOf(session.getUserID()), session.getRole(), restaurantId, itemId);
        return CommandSuccess.success(item, "Menu item \"" + item.name() + "\" removed");
    }
}
