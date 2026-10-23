package com.fortunategoods.domain.order;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fortunategoods.domain.handlers.BaseOrderHandler;
import com.fortunategoods.domain.handlers.ItemAvailabilityHandler;
import com.fortunategoods.domain.handlers.RestaurantStatusHandler;
import com.fortunategoods.domain.order.validation.OrderRequest;
import com.fortunategoods.domain.order.validation.OrderVerify;
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.domain.restaurant.menu.MenuItem;

// The order-checking chain: RestaurantStatusHandler first, then ItemAvailabilityHandler.
class CoRTest {

    private BaseOrderHandler chain;

    @BeforeEach
    void init() {
        chain = new RestaurantStatusHandler();
        chain.setNextHandler(new ItemAvailabilityHandler());
    }

    @Test
    void openRestaurantWithAvailableItemPasses() {
        assertDoesNotThrow(() -> OrderVerify.validate(request(RestaurantStatus.OPEN, MenuItemStatus.AVAILABLE), chain));
    }

    @Test
    void closedRestaurantIsRefused() {
        assertThrows(DomainRuleViolation.class, () -> OrderVerify.validate(request(RestaurantStatus.CLOSED, MenuItemStatus.AVAILABLE), chain));
    }

    // The item was available when the menu was loaded and sold out before the order was checked.
    @Test
    void itemSoldOutBeforeCheckingIsRefused() {
        OrderRequest request = request(RestaurantStatus.OPEN, MenuItemStatus.AVAILABLE);
        request.getOrderLines().get(0).getMenuItem().setStatus(MenuItemStatus.SOLD_OUT);

        assertThrows(DomainRuleViolation.class, () -> OrderVerify.validate(request, chain));
    }

    private static OrderRequest request(RestaurantStatus restaurantStatus, MenuItemStatus itemStatus) {
        MenuItem item = new MenuItem("m1", "r1", "Wrap", itemStatus, 4.50);
        Restaurant restaurant = new Restaurant("r1", "name", restaurantStatus, 1, 2, List.of(item));
        return new OrderRequest(restaurant, "12345678", item, List.of(OrderLine.fromMenuItem(item, 1)));
    }
}
