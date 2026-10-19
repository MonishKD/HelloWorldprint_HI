package com.fortunategoods.application.ord.menu;

import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.menu.RestaurantMenu;
import org.springframework.stereotype.Service;
import com.fortunategoods.domain.restaurant.menu.MenuItem;

import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;
import com.fortunategoods.technical.persistence.MenuItemRepository;

/*
    * This service is used to browse a restaurant's menu. It is used by the frontend to display the menu to the user.
    * It is also used by the restaurant staff to view their own menu.
*/
@Service
public class MenuService {

    private final RestaurantRepository restaurantRepository; // reads restaurants with their menu
    private final UserRepository userRepository;             // finds which restaurant a staff user works for
    private final MenuItemRepository menuItemRepository;

    // Spring gives us both repositories.
    public MenuService(RestaurantRepository restaurantRepository, UserRepository userRepository, MenuItemRepository menuItemRepository) {
        this.restaurantRepository = restaurantRepository;
        this.userRepository = userRepository;
        this.menuItemRepository = menuItemRepository;
    }

    // Returns the menu of this restaurant, or throws INVALID, FORBIDDEN or NOT_FOUND.
    public RestaurantMenu browseMenu(String restaurantId, String userId, UserRole role) {
        // Ids are database numbers, so anything else is an invalid request instead of a crash in the repository.
        if (restaurantId == null || !restaurantId.matches("\\d{1,9}")) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Restaurant id is invalid");
        }

        // Staff may only see their own restaurant's menu. Customers can see any.
        if (role == UserRole.RESTAURANT) {
            String ownRestaurantId = userRepository.findRestaurantIdByUserId(userId);
            if (!restaurantId.equals(ownRestaurantId)) {
                throw new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN,
                        "Restaurant staff can only view their own restaurant's menu");
            }
        }

        // findById returns null when the restaurant does not exist.
        Restaurant restaurant = restaurantRepository.findById(restaurantId);
        if (restaurant == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "Restaurant not found");
        }
        return RestaurantMenu.of(restaurant);
    }

    // Sets one menu item to available or sold out and returns the updated item (#89).
    // Throws FORBIDDEN, INVALID or NOT_FOUND, and saves nothing when it throws: the save is the last line.
    // restaurantId comes from the URL. The staff member's own restaurant comes from their account, never from the request.
    public MenuItem updateItemAvailability(String userId, UserRole role, String restaurantId, String itemId, MenuItemStatus newStat){
        //verifying that request is valid with staff, ID and new stat
        if(role!=UserRole.RESTAURANT){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN, "Only staff can change item status");
        }

        // Ids are database numbers, so anything else is an invalid request instead of a crash in the repository.
        if(restaurantId==null || !restaurantId.matches("\\d{1,9}")){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Restaurant id is invalid");
        }
        if(itemId==null || !itemId.matches("\\d{1,9}")){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Item id is invalid");
        }
        if(newStat==null){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Status must be available or sold_out");
        }

        MenuItem item = menuItemRepository.findById(itemId);
        // The item must exist AND be on the menu of the restaurant in the URL.
        if(item==null || !item.getRestaurantId().equals(restaurantId)){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "Item not found on this restaurant's menu");
        }
        //now checking if item belongs to right resto
        String staffRestaurantId = userRepository.findRestaurantIdByUserId(userId);
        //this function will verify that restaurant matches the item restaurant
        item.changeAvailability(staffRestaurantId,newStat);
        //takes the new set status
        menuItemRepository.updateStatus(itemId, item.getStatus());
        return item;
    }
}
