package com.fortunategoods.domain.restaurant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.domain.restaurant.menu.MenuItemRules;
import static com.fortunategoods.testsupport.TestData.wrap;

class MenuItemTest {

    @Test
    void availableAndSoldOutAreOpposites() {
        MenuItem available = new MenuItem("m1", "r1", "Veggie Wrap", MenuItemStatus.AVAILABLE, 4.50);
        MenuItem soldOut = new MenuItem("m1", "r1", "Veggie Wrap", MenuItemStatus.SOLD_OUT, 4.50);

        assertTrue(available.isAvailable());
        assertFalse(available.isSoldOut());
        assertTrue(soldOut.isSoldOut());
        assertFalse(soldOut.isAvailable());
    }

    @Test
    void rejectsInvalidData() {
        assertThrows(IllegalArgumentException.class, () -> new MenuItem(" ", "r1", "Wrap", MenuItemStatus.AVAILABLE, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new MenuItem("m1", null, "Wrap", MenuItemStatus.AVAILABLE, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new MenuItem("m1", "r1", "", MenuItemStatus.AVAILABLE, 1.0));
        assertThrows(NullPointerException.class, () -> new MenuItem("m1", "r1", "Wrap", null, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new MenuItem("m1", "r1", "Wrap", MenuItemStatus.AVAILABLE, -1.0));
        assertThrows(IllegalArgumentException.class, () -> new MenuItem("m1", "r1", "Wrap", MenuItemStatus.AVAILABLE, Double.NaN));
    }

    @Test
    void missingAvailabilityDoesNotChangeTheItem() {
        MenuItem item = wrap();

        assertRefused(DomainRuleViolation.Reason.INVALID, () -> item.changeAvailability("1", null));
        assertEquals(MenuItemStatus.AVAILABLE, item.getStatus());
    }

    @Test
    void itemMustExistOnTheRequestedMenu() {
        MenuItemRules.requireItemOnMenu(wrap(), "1");
        assertRefused(DomainRuleViolation.Reason.NOT_FOUND, () -> MenuItemRules.requireItemOnMenu(null, "1"));
        assertRefused(DomainRuleViolation.Reason.NOT_FOUND, () -> MenuItemRules.requireItemOnMenu(wrap(), "2"));
    }

    private void assertRefused(DomainRuleViolation.Reason reason, Runnable action) {
        assertEquals(reason, assertThrows(DomainRuleViolation.class, action::run).getReason());
    }
}
