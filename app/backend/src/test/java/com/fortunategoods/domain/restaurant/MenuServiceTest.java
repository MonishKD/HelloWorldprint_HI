package com.fortunategoods.domain.restaurant;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortunategoods.application.ord.menu.MenuService;
import com.fortunategoods.application.ord.menu.management.MenuItemInput;
import com.fortunategoods.application.ord.menu.management.MenuItemResponse;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.domain.restaurant.menu.RestaurantMenu;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.MenuItemRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;
import static com.fortunategoods.testsupport.TestData.wrap;

// MenuService: browsing a menu, and adding, editing and removing items through the rules in MenuItemRules.
// The repositories are mocked: restaurant "1" Cafe (staff user 8, item "10" Wrap) and restaurant "2" (staff user 9, item "30" Soup).
class MenuServiceTest {

    private static final MenuItemInput SALAD = new MenuItemInput(" Salad ", " Fresh greens ", 5.00, 10.00, MenuItemStatus.AVAILABLE);

    private MenuItemRepository items;
    private MenuService menuService;

    @BeforeEach
    void setup() {
        items = mock(MenuItemRepository.class);
        UserRepository users = mock(UserRepository.class);
        RestaurantRepository restaurants = mock(RestaurantRepository.class);
        when(users.findRestaurantIdByUserId("8")).thenReturn("1");
        when(users.findRestaurantIdByUserId("9")).thenReturn("2");
        when(restaurants.findById("1")).thenReturn(new Restaurant("1", "Cafe", RestaurantStatus.OPEN, 45.5, -73.6,
                List.of(wrap(), new MenuItem("11", "1", "Soup", MenuItemStatus.SOLD_OUT, 3.00))));
        when(items.findByRestaurantId("1")).thenReturn(List.of(wrap()));
        when(items.findByRestaurantId("2")).thenReturn(List.of(new MenuItem("30", "2", "Soup", MenuItemStatus.AVAILABLE, 3.00, 6.00)));
        when(items.add(eq("1"), any(), any(), any())).thenReturn("20");
        menuService = new MenuService(restaurants, users, items);
    }

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
    void staffBrowseOnlyTheirOwnMenu() {
        assertEquals("1", menuService.browseMenu("1", "8", UserRole.RESTAURANT).restaurantId());
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> menuService.browseMenu("1", "9", UserRole.RESTAURANT));
    }

    @Test
    void browsingRefusesUnknownRestaurantsBadIdsAndOtherRoles() {
        assertRefused(DomainRuleViolation.Reason.NOT_FOUND, () -> menuService.browseMenu("99", "7", UserRole.CUSTOMER));
        assertRefused(DomainRuleViolation.Reason.INVALID, () -> menuService.browseMenu("1 OR 1=1", "7", UserRole.CUSTOMER));
        for (UserRole role : new UserRole[] {null, UserRole.COURIER, UserRole.OPERATOR}) {
            assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> menuService.browseMenu("1", "7", role));
        }
    }

    @Test
    void staffAddsAnItemToTheirOwnMenu() {
        MenuItemResponse added = menuService.addMenuItem("8", UserRole.RESTAURANT, "1", SALAD);

        assertEquals("20", added.id());
        assertEquals("Salad", added.name());
        assertEquals("Fresh greens", added.description());
        assertEquals(5.00, added.price());
        assertEquals(10.00, added.originalPrice());
        assertEquals(MenuItemStatus.AVAILABLE, added.status());
        ArgumentCaptor<MenuItem> saved = ArgumentCaptor.forClass(MenuItem.class);
        verify(items).add(eq("1"), saved.capture(), eq("Fresh greens"), eq(10.00));
        assertEquals("Salad", saved.getValue().getName());
    }

    @Test
    void staffEditsTheirOwnItem() {
        MenuItemInput edit = new MenuItemInput("Big Wrap", "Now bigger", 6.00, null, MenuItemStatus.SOLD_OUT);

        assertEquals("Big Wrap", menuService.editMenuItem("8", UserRole.RESTAURANT, "1", "10", edit).name());

        ArgumentCaptor<MenuItem> saved = ArgumentCaptor.forClass(MenuItem.class);
        verify(items).update(saved.capture(), eq("Now bigger"), isNull());
        assertEquals("10", saved.getValue().getId());
        assertEquals("Big Wrap", saved.getValue().getName());
        assertEquals(6.00, saved.getValue().getPrice());
        assertNull(saved.getValue().getOriginalPrice());
        assertEquals(MenuItemStatus.SOLD_OUT, saved.getValue().getStatus());
    }

    // Removing only hides the item; a past order line keeps its own name and price.
    @Test
    void removedItemIsHiddenAndPastOrdersKeepIt() {
        OrderLine pastOrderLine = OrderLine.fromMenuItem(wrap(), 2);

        menuService.removeMenuItem("8", UserRole.RESTAURANT, "1", "10");

        verify(items).hide("10");
        assertEquals("Wrap", pastOrderLine.getName());
        assertEquals(4.50, pastOrderLine.getPrice());
        assertEquals("10", pastOrderLine.getMenuItemId());
    }

    @Test
    void changesRefuseOtherRestaurantsAndOtherRoles() {
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> menuService.addMenuItem("8", UserRole.RESTAURANT, "2", SALAD));
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> menuService.editMenuItem("8", UserRole.RESTAURANT, "2", "30", SALAD));
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> menuService.removeMenuItem("8", UserRole.RESTAURANT, "2", "30"));
        assertRefused(DomainRuleViolation.Reason.NOT_FOUND, () -> menuService.editMenuItem("8", UserRole.RESTAURANT, "1", "30", SALAD));
        assertRefused(DomainRuleViolation.Reason.NOT_FOUND, () -> menuService.removeMenuItem("8", UserRole.RESTAURANT, "1", "30"));
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> menuService.addMenuItem("7", UserRole.CUSTOMER, "1", SALAD));
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> menuService.removeMenuItem("7", UserRole.COURIER, "1", "10"));
        verifyNothingSaved();
    }

    @Test
    void invalidItemsAreRefused() {
        assertInvalid(new MenuItemInput(null, null, 5.00, null, MenuItemStatus.AVAILABLE), "Give the item a name");
        assertInvalid(new MenuItemInput("  ", null, 5.00, null, MenuItemStatus.AVAILABLE), "Give the item a name");
        assertInvalid(new MenuItemInput("Salad", null, 0.0, null, MenuItemStatus.AVAILABLE), "The price must be above zero");
        assertInvalid(new MenuItemInput("Salad", null, -1.0, null, MenuItemStatus.AVAILABLE), "The price must be above zero");
        assertInvalid(new MenuItemInput("Salad", null, null, null, MenuItemStatus.AVAILABLE), "The price must be above zero");
        assertInvalid(new MenuItemInput("Salad", null, 5.00, 4.00, MenuItemStatus.AVAILABLE), "The original price can't be lower than the price");
        assertInvalid(new MenuItemInput("Salad", null, 5.00, null, null), "Status must be available or sold_out");
        assertInvalid(new MenuItemInput("x".repeat(101), null, 5.00, null, MenuItemStatus.AVAILABLE), "The name can have at most 100 characters");
        verifyNothingSaved();
    }

    // The repository leaves removed items out of the menu, so they can't be edited or removed again.
    @Test
    void removedUnknownAndBadItemIdsAreRefused() {
        when(items.findByRestaurantId("1")).thenReturn(List.of());

        assertRefused(DomainRuleViolation.Reason.NOT_FOUND, () -> menuService.editMenuItem("8", UserRole.RESTAURANT, "1", "10", SALAD));
        assertRefused(DomainRuleViolation.Reason.NOT_FOUND, () -> menuService.removeMenuItem("8", UserRole.RESTAURANT, "1", "10"));
        assertRefused(DomainRuleViolation.Reason.NOT_FOUND, () -> menuService.removeMenuItem("8", UserRole.RESTAURANT, "1", "99"));
        assertRefused(DomainRuleViolation.Reason.INVALID, () -> menuService.removeMenuItem("8", UserRole.RESTAURANT, "1", "abc"));
        verifyNothingSaved();
    }

    private void verifyNothingSaved() {
        verify(items, never()).add(anyString(), any(), any(), any());
        verify(items, never()).update(any(), any(), any());
        verify(items, never()).hide(anyString());
    }

    private void assertInvalid(MenuItemInput input, String message) {
        DomainRuleViolation e = assertThrows(DomainRuleViolation.class, () -> menuService.addMenuItem("8", UserRole.RESTAURANT, "1", input));
        assertEquals(DomainRuleViolation.Reason.INVALID, e.getReason());
        assertEquals(message, e.getMessage());
    }

    private void assertRefused(DomainRuleViolation.Reason reason, Runnable action) {
        assertEquals(reason, assertThrows(DomainRuleViolation.class, action::run).getReason());
    }
}
