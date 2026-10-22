package com.fortunategoods.application.ord.menu.browsing;

import com.fortunategoods.application.ord.menu.MenuService;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import org.springframework.stereotype.Service;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.restaurant.menu.RestaurantMenu;

/*
    Builds the menu commands and sends them through CommandInvoker (#82), so menu browsing takes
    the same authenticated Command path as the other ORD operations.
    The REST endpoint (#84) calls browseMenu() with the session of the validated request.
*/
@Service
public class MenuCommands {

    private final CommandInvoker commandInvoker; // checks session and role, then runs the command
    private final MenuService menuService;       // handed to each command as its receiver

    // Spring gives us both of these.
    public MenuCommands(CommandInvoker commandInvoker, MenuService menuService) {
        this.commandInvoker = commandInvoker;
        this.menuService = menuService;
    }

    // Returns the menu, or throws the invoker's failure (not signed in, wrong role, not found, invalid id...).
    // CommandSuccess<RestaurantMenu>: a success result whose data is a RestaurantMenu.
    public CommandSuccess<RestaurantMenu> browseMenu(Session session, String restaurantId) {
        // 1. build the command, 2. let the invoker check and run it
        BrowseMenuCommand command = new BrowseMenuCommand(menuService, session, restaurantId);
        return commandInvoker.process(command, session);
    }
}
