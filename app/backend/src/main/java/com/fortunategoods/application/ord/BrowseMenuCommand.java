package com.fortunategoods.application.ord;

import java.util.Set;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.restaurant.MenuService;
import com.fortunategoods.domain.restaurant.RestaurantMenu;
import com.fortunategoods.domain.user.UserRole;

/*
    browseMenuCommand (#82): returns one restaurant's menu. MenuService is the receiver.
    Run it through CommandInvoker.process(command, session) so the session and role are checked first.
    The user's id and role come from the Session, never from the request.
*/
// Command<RestaurantMenu>: this command's result (its data) is a RestaurantMenu.
public final class BrowseMenuCommand implements Command<RestaurantMenu> {

    private final MenuService menuService; // receiver: does the real work
    private final Session session;         // who is asking (id and role)
    private final String restaurantId;     // which menu they want

    public BrowseMenuCommand(MenuService menuService, Session session, String restaurantId) {
        this.menuService = menuService;
        this.session = session;
        this.restaurantId = restaurantId;
    }

    // Roles allowed to browse (CUSTOMER and RESTAURANT). The invoker checks this before execute().
    @Override
    public Set<UserRole> validRoles() {
        return OrdModulePermissions.browseMenuRoles();
    }

    // Only runs after the invoker's checks pass. Asks MenuService for the menu and wraps it in a success result.
    @Override
    public CommandSuccess<RestaurantMenu> execute() {
        RestaurantMenu menu = menuService.browseMenu(restaurantId, String.valueOf(session.getUserID()), session.getRole());
        return CommandSuccess.success(menu, "Menu of " + menu.restaurantName() + " loaded");
    }
}
