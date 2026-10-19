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

    public void updateItemAvailability(String userId, UserRole role, String itemId, MenuItemStatus newStat){
        //verifying that request is valid with staff, ID and new stat
        if(role!=UserRole.RESTAURANT){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN, "Only staff can change item status");
        }

        if(itemId==null){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "invalid Item ID");
        }
        if(newStat==null){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "invalid new Status");
        }

        MenuItem item = menuItemRepository.findById(itemId);
        if(item==null){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "item not found");
        }
        //now checking if item belongs to right resto
        String restaurantId = userRepository.findRestaurantIdByUserId(userId);
        //this function will verify that restaurant matches the item restaurant
        item.changeAvailability(restaurantId,newStat);
        //takes the new set status
        menuItemRepository.updateStatus(itemId, item.getStatus());
    }
}
