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
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.MenuItemRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;

// Tests the MenuService's add, edit and remove menu item operations, through the domain rules in MenuItemRules:
// valid, invalid, forbidden and not found. The repositories are mocked:
// restaurant "1" (staff user 8, item "10" Wrap) and restaurant "2" (staff user 9, item "30" Soup).
class MenuEditingTest {

    private static final MenuItem WRAP = new MenuItem("10", "1", "Wrap", MenuItemStatus.AVAILABLE, 4.50, 9.00);
    private static final MenuItem SOUP = new MenuItem("30", "2", "Soup", MenuItemStatus.AVAILABLE, 3.00, 6.00);
    private static final MenuItemInput SALAD = new MenuItemInput(" Salad ", " Fresh greens ", 5.00, 10.00, MenuItemStatus.AVAILABLE);

    private MenuItemRepository items;
    private MenuService menuService;

    @BeforeEach
    void setup() {
        items = mock(MenuItemRepository.class);
        UserRepository users = mock(UserRepository.class);
        when(users.findRestaurantIdByUserId("8")).thenReturn("1");
        when(users.findRestaurantIdByUserId("9")).thenReturn("2");
        when(items.findByRestaurantId("1")).thenReturn(List.of(WRAP));
        when(items.findByRestaurantId("2")).thenReturn(List.of(SOUP));
        when(items.add(eq("1"), any(), any(), any())).thenReturn("20");

        menuService = new MenuService(mock(RestaurantRepository.class), users, items);
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

        MenuItemResponse edited = menuService.editMenuItem("8", UserRole.RESTAURANT, "1", "10", edit);

        assertEquals("Big Wrap", edited.name());
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
        OrderLine pastOrderLine = OrderLine.fromMenuItem(WRAP, 2);

        menuService.removeMenuItem("8", UserRole.RESTAURANT, "1", "10");

        verify(items).hide("10");
        assertEquals("Wrap", pastOrderLine.getName());
        assertEquals(4.50, pastOrderLine.getPrice());
        assertEquals("10", pastOrderLine.getMenuItemId());
    }

    @Test
    void anotherRestaurantsMenuIsRefused() {
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> menuService.addMenuItem("8", UserRole.RESTAURANT, "2", SALAD));
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> menuService.editMenuItem("8", UserRole.RESTAURANT, "2", "30", SALAD));
        assertRefused(DomainRuleViolation.Reason.FORBIDDEN, () -> menuService.removeMenuItem("8", UserRole.RESTAURANT, "2", "30"));

        verifyNothingSaved();
    }

    @Test
    void otherRestaurantsItemThroughOwnUrlIsNotFound() {
        assertRefused(DomainRuleViolation.Reason.NOT_FOUND, () -> menuService.editMenuItem("8", UserRole.RESTAURANT, "1", "30", SALAD));
        assertRefused(DomainRuleViolation.Reason.NOT_FOUND, () -> menuService.removeMenuItem("8", UserRole.RESTAURANT, "1", "30"));

        verifyNothingSaved();
    }

    @Test
    void otherRolesAreRefused() {
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

    private void assertRefused(DomainRuleViolation.Reason reason, Runnable change) {
        DomainRuleViolation e = assertThrows(DomainRuleViolation.class, change::run);
        assertEquals(reason, e.getReason());
    }
}
