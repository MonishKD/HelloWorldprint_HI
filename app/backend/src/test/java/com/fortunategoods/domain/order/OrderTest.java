package com.fortunategoods.domain.order;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.domain.restaurant.MenuItemStatus;


/*
    This class encompasses multiple edge case scenarios from Order and OrderLine
*/
public class OrderTest {

    @Test
    void newOrderPlaced(){
        MenuItem mi = new MenuItem("10", "1", "Mi", MenuItemStatus.AVAILABLE, 4.50, 8.00);
        OrderLine ol = OrderLine.fromMenuItem(mi,3);
        Order o = Order.placed("7", "1", "Nothing", List.of(ol));

        // Check if order information matches with inputted value
        assertEquals("7", o.getCustomerId());
        assertEquals("1", o.getRestaurantId());
        assertEquals(OrderStatus.PLACED, o.getStatus());
        assertEquals("Nothing", o.getSpecialInstructions());
        assertEquals(1, o.getLines().size());
        assertEquals(3, o.getLines().get(0).getQuantity());
        assertNull(o.getId());
    }

    @Test
    void orderLineMenuItem() {
        MenuItem mi = new MenuItem("10", "2", "Soup", MenuItemStatus.AVAILABLE, 4.50, 8.00);

        OrderLine ol = OrderLine.fromMenuItem(mi, 3);

        // Check if MenuItem information matches that of OrderLine on the common variables they have
        assertEquals("10", ol.getMenuItemId());
        assertEquals("Soup", ol.getName());
        assertEquals(4.50, ol.getPrice(), 0.001);
        assertEquals(8.00, ol.getOriginalPrice(), 0.001);
    }

    @Test
    void ogPriceNotRequired() {
        MenuItem mi = new MenuItem("11", "1", "Soup", MenuItemStatus.AVAILABLE, 3.00);

        OrderLine ol = OrderLine.fromMenuItem(mi, 1);

        // Original price in an OL can be null
        assertNull(ol.getOriginalPrice());
    }

    @Test
    void zeroQuantity() {
        MenuItem mi = new MenuItem("10", "1", "Wrap", MenuItemStatus.AVAILABLE, 4.50);

        // Throw error if there is quantity of 0
        DomainRuleViolation error = assertThrows(DomainRuleViolation.class,() -> OrderLine.fromMenuItem(mi, 0));

        // Check if error's reason is INVALID
        assertEquals(DomainRuleViolation.Reason.INVALID, error.getReason());
    }

    @Test
    void negQuantity() {
        MenuItem mi = new MenuItem("10", "1", "Wrap", MenuItemStatus.AVAILABLE, 4.50);

        // Throw error if there is quantity < 0
        DomainRuleViolation error = assertThrows(DomainRuleViolation.class,() -> OrderLine.fromMenuItem(mi, -1));

        // Check if error's reason is INVALID
        assertEquals(DomainRuleViolation.Reason.INVALID, error.getReason());
    }

    @Test
    void multipleMenuItemsSameRestaurant() {
        MenuItem mi = new MenuItem("10", "1", "Wrap", MenuItemStatus.AVAILABLE, 4.50);
        MenuItem mii = new MenuItem("11", "1", "Soup", MenuItemStatus.AVAILABLE, 3.00);
        Order o = Order.placed("7", "1", null, List.of(OrderLine.fromMenuItem(mi, 2), OrderLine.fromMenuItem(mii, 1)));
        assertEquals(2, o.getLines().size());
    }

    @Test
    void multipleMenuItemsDifferentRestaurant() {
        MenuItem mi = new MenuItem("10", "1", "Wrap", MenuItemStatus.AVAILABLE, 4.50);
        MenuItem mii = new MenuItem("11", "2", "Soup", MenuItemStatus.AVAILABLE, 3.00);
        assertThrows(DomainRuleViolation.class, () -> Order.placed("7", "1", null, List.of(OrderLine.fromMenuItem(mi, 2), OrderLine.fromMenuItem(mii, 1))));
    }

    @Test
    void emptyOrder() {
        assertThrows(DomainRuleViolation.class, () -> Order.placed("7", "1", null, List.of()));
    }

    @Test
    void instructionsOver500Characters() {
        MenuItem mi = new MenuItem("10", "1", "Wrap", MenuItemStatus.AVAILABLE, 4.50);
        assertThrows(DomainRuleViolation.class,() -> Order.placed("7", "1", "a".repeat(501), List.of(OrderLine.fromMenuItem(mi, 1))));
    }

    @Test
    void instructionsUnder500() {
        MenuItem mi = new MenuItem("10", "1", "Mi", MenuItemStatus.AVAILABLE, 4.50);
        OrderLine ol = OrderLine.fromMenuItem(mi, 2);

        // List of possible valid special instructions
        String[] instructions = {null, "", "a".repeat(500)};

        for (String instruction : instructions) {
            Order order = Order.placed("7", "1", instruction, List.of(ol));

            // Ensure both instructions and order's instruction are equal, and that order was placed
            assertEquals(instruction, order.getSpecialInstructions());
            assertEquals(OrderStatus.PLACED, order.getStatus());
        }
    }

    @Test
    void missingCustomerIDs() {
        MenuItem mi = new MenuItem("10", "1", "Mi", MenuItemStatus.AVAILABLE, 4.50);
        OrderLine ol = OrderLine.fromMenuItem(mi, 2);

        // List of possible invalidCustomer IDs
        String[] invalidCustomers = {null, "", "   "};

        for (String customerId : invalidCustomers) {
            DomainRuleViolation error = assertThrows(DomainRuleViolation.class,() -> Order.placed(customerId, "1", null, List.of(ol)));
            assertEquals(DomainRuleViolation.Reason.INVALID, error.getReason());
        }
    }

    @Test
    void missingRestaurantIds() {
        MenuItem mi = new MenuItem("10", "1", "Mi", MenuItemStatus.AVAILABLE, 4.50, 8.50);
        OrderLine ol = OrderLine.fromMenuItem(mi, 2);

        // List of possible invalid restaurantIds
        String[] invalidRestaurants = {null, "", "   "};

        for (String restaurantId : invalidRestaurants) {
            DomainRuleViolation error = assertThrows(DomainRuleViolation.class, () -> Order.placed("7", restaurantId, null, List.of(ol)));
            assertEquals(DomainRuleViolation.Reason.INVALID, error.getReason());
        }
    }

    @Test
    void nullStatus() {
        MenuItem mi = new MenuItem("10", "1", "Mi", MenuItemStatus.AVAILABLE, 4.50, 8.50);
        OrderLine ol = OrderLine.fromMenuItem(mi, 2);

        DomainRuleViolation error = assertThrows(DomainRuleViolation.class, () -> new Order("100", "7", "1", null, null, null, null, null, null, null, List.of(ol)));
        assertEquals(DomainRuleViolation.Reason.INVALID,error.getReason());
    }

    @Test
    void nullListOrderLines() {
        assertThrows(DomainRuleViolation.class, () -> Order.placed("7", "1", null, null));
    }

    @Test
    void nullOrderLines() {
        // singletonList to allow null objects
        List<OrderLine> ol = java.util.Collections.singletonList(null);
        assertThrows(DomainRuleViolation.class,() -> Order.placed("7", "1", null, ol));
    }

    @Test
    void nullMenuItem() {
        // Through the fromMenuItem() which calls the constructor
        assertThrows(DomainRuleViolation.class, () -> OrderLine.fromMenuItem(null, 1));

        // Through the constructor itself
        assertThrows(DomainRuleViolation.class, () -> new OrderLine(null, "10", 1, "Wrap", 4.50, null, null));
    }

    @Test
    void badMenuItemIds() {
        MenuItem mi = new MenuItem("10", "1", "Mi", MenuItemStatus.AVAILABLE, 4.50, 8.50);

        // List of possible invalid menu item ids
        String[] invalidIds = {null, "", "999"};

        for (String menuItemId : invalidIds) {
            DomainRuleViolation error = assertThrows(DomainRuleViolation.class, () -> new OrderLine(null, menuItemId, 1, "Mi", 4.50, null, mi));
            assertEquals(DomainRuleViolation.Reason.INVALID, error.getReason());
        }
    }

    @Test
    void missingOrderLineNames() {
        MenuItem mi = new MenuItem("10", "1", "Mi", MenuItemStatus.AVAILABLE, 4.50, 8.50);
        // List of possible missing names
        String[] invalidNames = {null, "", "   "};

        for (String name : invalidNames) {
            assertThrows(DomainRuleViolation.class, () -> new OrderLine(null, "10", 1, name, 4.50, null, mi));
        }
    }

    @Test
    void invalidOrderLinePrices() {
        MenuItem mi = new MenuItem("10", "1", "Mi", MenuItemStatus.AVAILABLE, 4.50, 8.50);

        // List of possible invalid prices
        double[] invalidPrices = {-0.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};

        for (double price : invalidPrices) {
            assertThrows(DomainRuleViolation.class, () -> new OrderLine(null, "10", 1, "Wrap", price, null, mi));
        }
    }

    @Test
    void invalidOrderLineOGPrices() {
        MenuItem mi = new MenuItem("10", "1", "Mi", MenuItemStatus.AVAILABLE, 4.50, 8.50);

        double[] invalidPrices = {-0.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};

        for (double originalPrice : invalidPrices) {
            assertThrows(DomainRuleViolation.class, () -> new OrderLine(null, "10", 1, "Wrap", 4.50, originalPrice, mi));
        }
    }

}
