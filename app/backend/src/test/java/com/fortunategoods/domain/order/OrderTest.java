package com.fortunategoods.domain.order;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
import static com.fortunategoods.testsupport.TestData.wrap;

// Edge cases of Order and OrderLine.
class OrderTest {

    private static final double[] BAD_PRICES = {-0.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};

    @Test
    void newOrderIsPlacedWithItsLines() {
        Order order = Order.placed("7", "1", "Nothing", List.of(OrderLine.fromMenuItem(wrap(), 3)));

        assertNull(order.getId());
        assertEquals("7", order.getCustomerId());
        assertEquals("1", order.getRestaurantId());
        assertEquals(OrderStatus.PLACED, order.getStatus());
        assertEquals("Nothing", order.getSpecialInstructions());
        assertEquals(1, order.getLines().size());
        assertEquals(3, order.getLines().get(0).getQuantity());
    }

    // The line keeps a copy of the item's id, name and prices. The original price is optional.
    @Test
    void orderLineCopiesTheMenuItem() {
        OrderLine line = OrderLine.fromMenuItem(wrap(), 3);
        assertEquals("10", line.getMenuItemId());
        assertEquals("Wrap", line.getName());
        assertEquals(4.50, line.getPrice(), 0.001);
        assertEquals(9.00, line.getOriginalPrice(), 0.001);

        assertNull(OrderLine.fromMenuItem(new MenuItem("11", "1", "Soup", MenuItemStatus.AVAILABLE, 3.00), 1).getOriginalPrice());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void quantityMustBeAboveZero(int quantity) {
        DomainRuleViolation error = assertThrows(DomainRuleViolation.class, () -> OrderLine.fromMenuItem(wrap(), quantity));
        assertEquals(DomainRuleViolation.Reason.INVALID, error.getReason());
    }

    @Test
    void allLinesMustComeFromTheOrdersRestaurant() {
        MenuItem soup = new MenuItem("11", "1", "Soup", MenuItemStatus.AVAILABLE, 3.00);
        assertEquals(2, Order.placed("7", "1", null, List.of(OrderLine.fromMenuItem(wrap(), 2), OrderLine.fromMenuItem(soup, 1))).getLines().size());

        MenuItem otherSoup = new MenuItem("11", "2", "Soup", MenuItemStatus.AVAILABLE, 3.00);
        assertThrows(DomainRuleViolation.class,
                () -> Order.placed("7", "1", null, List.of(OrderLine.fromMenuItem(wrap(), 2), OrderLine.fromMenuItem(otherSoup, 1))));
    }

    @Test
    void emptyMissingOrNullLinesAreRefused() {
        assertThrows(DomainRuleViolation.class, () -> Order.placed("7", "1", null, List.of()));
        assertThrows(DomainRuleViolation.class, () -> Order.placed("7", "1", null, null));
        assertThrows(DomainRuleViolation.class, () -> Order.placed("7", "1", null, Collections.singletonList(null)));
    }

    @Test
    void instructionsCanBeEmptyButAtMost500Characters() {
        for (String instructions : new String[] {null, "", "a".repeat(500)}) {
            Order order = Order.placed("7", "1", instructions, List.of(line()));
            assertEquals(instructions, order.getSpecialInstructions());
            assertEquals(OrderStatus.PLACED, order.getStatus());
        }
        assertThrows(DomainRuleViolation.class, () -> Order.placed("7", "1", "a".repeat(501), List.of(line())));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void customerAndRestaurantAreRequired(String id) {
        assertEquals(DomainRuleViolation.Reason.INVALID,
                assertThrows(DomainRuleViolation.class, () -> Order.placed(id, "1", null, List.of(line()))).getReason());
        assertEquals(DomainRuleViolation.Reason.INVALID,
                assertThrows(DomainRuleViolation.class, () -> Order.placed("7", id, null, List.of(line()))).getReason());
    }

    @Test
    void statusIsRequired() {
        DomainRuleViolation error = assertThrows(DomainRuleViolation.class,
                () -> new Order("100", "7", "1", null, null, null, null, null, null, null, List.of(line())));
        assertEquals(DomainRuleViolation.Reason.INVALID, error.getReason());
    }

    @Test
    void orderLineNeedsItsMenuItemAMatchingIdAndAName() {
        assertThrows(DomainRuleViolation.class, () -> OrderLine.fromMenuItem(null, 1));
        assertThrows(DomainRuleViolation.class, () -> new OrderLine(null, "10", 1, "Wrap", 4.50, null, null));
        for (String menuItemId : new String[] {null, "", "999"}) {
            DomainRuleViolation error = assertThrows(DomainRuleViolation.class, () -> new OrderLine(null, menuItemId, 1, "Wrap", 4.50, null, wrap()));
            assertEquals(DomainRuleViolation.Reason.INVALID, error.getReason());
        }
        for (String name : new String[] {null, "", "   "}) {
            assertThrows(DomainRuleViolation.class, () -> new OrderLine(null, "10", 1, name, 4.50, null, wrap()));
        }
    }

    @Test
    void orderLinePricesMustBeValidNumbers() {
        for (double bad : BAD_PRICES) {
            assertThrows(DomainRuleViolation.class, () -> new OrderLine(null, "10", 1, "Wrap", bad, null, wrap()));
            assertThrows(DomainRuleViolation.class, () -> new OrderLine(null, "10", 1, "Wrap", 4.50, bad, wrap()));
        }
    }

    private static OrderLine line() {
        return OrderLine.fromMenuItem(wrap(), 2);
    }
}
