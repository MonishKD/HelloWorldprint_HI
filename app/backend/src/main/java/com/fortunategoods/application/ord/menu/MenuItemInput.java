package com.fortunategoods.application.ord.menu;

import com.fortunategoods.domain.restaurant.MenuItemStatus;

/*
    What the restaurant typed for a new or edited menu item.
    Nothing is checked here: MenuService checks it with MenuItemRules, so refusals go through CommandInvoker.
    price and originalPrice are Double (not double) so a missing price can be told apart from 0.
    status is null when the text was missing or was not "available" / "sold_out".
*/
public record MenuItemInput(String name, String description, Double price, Double originalPrice, MenuItemStatus status) {
}
