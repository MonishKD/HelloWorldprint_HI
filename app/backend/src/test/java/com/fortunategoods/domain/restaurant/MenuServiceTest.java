package com.fortunategoods.domain.restaurant;

import com.fortunategoods.application.ord.menu.MenuService;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.domain.restaurant.menu.RestaurantMenu;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import org.junit.jupiter.api.Test;

import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;
import com.fortunategoods.technical.persistence.MenuItemRepository;

class MenuServiceTest {
    // Fake repositories: no database, restaurant "1" exists and user "8" works for it.
    // The MenuService is tested in isolation, so the repositories are not called for any other restaurant or user.
    private final RestaurantRepository restaurants = new RestaurantRepository(null, null) {
        @Override
        public Restaurant findById(String id) {
            if (!id.equals("1")) {
                return null;
            }
            return new Restaurant("1", "Cafe", RestaurantStatus.OPEN, 45.5, -73.6, List.of(
                    new MenuItem("10", "1", "Wrap", MenuItemStatus.AVAILABLE, 4.50),
                    new MenuItem("11", "1", "Soup", MenuItemStatus.SOLD_OUT, 3.00)));
        }
    };

    private final UserRepository users = new UserRepository(null) {
        @Override
        public String findRestaurantIdByUserId(String userId) {
            return userId.equals("8") ? "1" : null;
        }
    };

    private final MenuService menuService = new MenuService(restaurants, users, mock(MenuItemRepository.class));
    // The tests below check that the MenuService behaves correctly for different users and restaurants, including access control and error handling.
    @Test
    void customerSeesItemsWithStatusAndPrice() {
        RestaurantMenu menu = menuService.browseMenu("1", "7", UserRole.CUSTOMER);

        assertEquals("Cafe", menu.restaurantName());
        assertEquals(2, menu.items().size());
        assertEquals(MenuItemStatus.AVAILABLE, menu.items().get(0).getStatus());
        assertEquals(MenuItemStatus.SOLD_OUT, menu.items().get(1).getStatus());
        assertEquals(4.50, menu.items().get(0).getPrice());
    }

    @Test
    void staffSeesTheirOwnMenu() {
        RestaurantMenu menu = menuService.browseMenu("1", "8", UserRole.RESTAURANT);

        assertEquals("1", menu.restaurantId());
    }

    @Test
    void staffOfAnotherRestaurantIsRefused() {
        DomainRuleViolation e = assertThrows(DomainRuleViolation.class,
                () -> menuService.browseMenu("1", "9", UserRole.RESTAURANT));

        assertEquals(DomainRuleViolation.Reason.FORBIDDEN, e.getReason());
    }

    @Test
    void unknownRestaurantIsNotFound() {
        DomainRuleViolation e = assertThrows(DomainRuleViolation.class,
                () -> menuService.browseMenu("99", "7", UserRole.CUSTOMER));

        assertEquals(DomainRuleViolation.Reason.NOT_FOUND, e.getReason());
    }

    @Test
    void malformedRestaurantIdIsInvalid() {
        DomainRuleViolation e = assertThrows(DomainRuleViolation.class,
                () -> menuService.browseMenu("1 OR 1=1", "7", UserRole.CUSTOMER));

        assertEquals(DomainRuleViolation.Reason.INVALID, e.getReason());
    }

}
