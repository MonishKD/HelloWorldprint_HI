package com.fortunategoods.domain.restaurant;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.fortunategoods.application.ord.FakeMenuDatabase;
import com.fortunategoods.application.ord.menu.management.MenuItemInput;
import com.fortunategoods.application.ord.menu.management.MenuItemResponse;
import com.fortunategoods.application.ord.menu.MenuService;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.domain.user.UserRole;

// The database is FakeMenuDatabase: restaurant "1" (staff user 8, item "10" Wrap) and restaurant "2" (staff user 9, item "30" Soup).
// Tests the MenuService's add, edit and remove menu item commands, through the domain rules in MenuItemRules across all the scenarios: valid, invalid, forbidden and not found.
class MenuEditingTest {

    private final FakeMenuDatabase db = new FakeMenuDatabase();
    private final MenuService menuService = db.menuService();

    private static final MenuItemInput SALAD = new MenuItemInput(" Salad ", " Fresh greens ", 5.00, 10.00, MenuItemStatus.AVAILABLE);
    
    @Test
    void staffAddsAnItemToTheirOwnMenu() {
        MenuItemResponse added = menuService.addMenuItem("8", UserRole.RESTAURANT, "1", SALAD);

        assertEquals("20", added.id());
        assertEquals("Salad", added.name());
        assertEquals("Fresh greens", added.description());
        assertEquals(5.00, added.price());
        assertEquals(10.00, added.originalPrice());
        assertEquals(MenuItemStatus.AVAILABLE, added.status());
        assertTrue(customerMenuHas("20"));
    }

    @Test
    void staffEditsTheirOwnItem() {
        MenuItemInput edit = new MenuItemInput("Big Wrap", "Now bigger", 6.00, null, MenuItemStatus.SOLD_OUT);

        MenuItemResponse edited = menuService.editMenuItem("8", UserRole.RESTAURANT, "1", "10", edit);

        assertEquals("Big Wrap", edited.name());
        assertEquals("Big Wrap", db.items.get("10").getName());
        assertEquals(6.00, db.items.get("10").getPrice());
        assertNull(db.items.get("10").getOriginalPrice());
        assertEquals(MenuItemStatus.SOLD_OUT, db.items.get("10").getStatus());
        assertEquals("Now bigger", db.descriptions.get("10"));
    }

    @Test
    void removedItemLeavesTheMenuButPastOrdersKeepIt() {
        OrderLine pastOrderLine = OrderLine.fromMenuItem(db.items.get("10"), 2);

        menuService.removeMenuItem("8", UserRole.RESTAURANT, "1", "10");

        assertFalse(customerMenuHas("10"));
        assertNotNull(db.items.get("10"));                // hidden, not deleted
        assertEquals("Wrap", pastOrderLine.getName());
        assertEquals(4.50, pastOrderLine.getPrice());
        assertEquals("10", pastOrderLine.getMenuItemId());
    }

    @Test
    void anotherRestaurantsMenuIsRefused() {
        Map<String, MenuItem> before = Map.copyOf(db.items);

        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> menuService.addMenuItem("8", UserRole.RESTAURANT, "2", SALAD));
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> menuService.editMenuItem("8", UserRole.RESTAURANT, "2", "30", SALAD));
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> menuService.removeMenuItem("8", UserRole.RESTAURANT, "2", "30"));

        assertEquals(before, db.items);
        assertTrue(db.hidden.isEmpty());
    }

    @Test
    void otherRestaurantsItemThroughOwnUrlIsNotFound() {
        assertRefused(DomainRuleViolation.Reason.NOT_FOUND, () -> menuService.editMenuItem("8", UserRole.RESTAURANT, "1", "30", SALAD));
        assertRefused(DomainRuleViolation.Reason.NOT_FOUND, () -> menuService.removeMenuItem("8", UserRole.RESTAURANT, "1", "30"));
        assertFalse(db.hidden.contains("30"));
    }

    @Test
    void otherRolesAreRefused() {
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> menuService.addMenuItem("7", UserRole.CUSTOMER, "1", SALAD));
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> menuService.removeMenuItem("7", UserRole.COURIER, "1", "10"));
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

        assertEquals(2, db.items.size());
    }

    @Test
    void removedUnknownAndBadItemIdsAreRefused() {
        menuService.removeMenuItem("8", UserRole.RESTAURANT, "1", "10");

        assertRefused(DomainRuleViolation.Reason.NOT_FOUND, () -> menuService.editMenuItem("8", UserRole.RESTAURANT, "1", "10", SALAD));
        assertRefused(DomainRuleViolation.Reason.NOT_FOUND, () -> menuService.removeMenuItem("8", UserRole.RESTAURANT, "1", "10"));
        assertRefused(DomainRuleViolation.Reason.NOT_FOUND, () -> menuService.removeMenuItem("8", UserRole.RESTAURANT, "1", "99"));
        assertRefused(DomainRuleViolation.Reason.INVALID, () -> menuService.removeMenuItem("8", UserRole.RESTAURANT, "1", "abc"));
    }

    private boolean customerMenuHas(String itemId) {
        return menuService.browseMenu("1", "7", UserRole.CUSTOMER).items().stream()
                .anyMatch(item -> item.getId().equals(itemId));
    }

    private void assertInvalid(MenuItemInput input, String message) {
        DomainRuleViolation e = assertThrows(DomainRuleViolation.class, () -> menuService.addMenuItem("8", UserRole.RESTAURANT, "1", input));
        assertEquals(DomainRuleViolation.Reason.INVALID, e.getReason());
        assertEquals(message, e.getMessage());
    }

    private void assertRefused(DomainRuleViolation.Reason reason, Runnable change) {
        DomainRuleViolation e = assertThrows(DomainRuleViolation.class, change::run);
        assertEquals(reason, e.getReason());
    }
}
