package com.fortunategoods.domain.order.validation;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.order.OrderStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fortunategoods.domain.handlers.BaseOrderHandler;
import com.fortunategoods.domain.order.state.PlacedState;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.domain.restaurant.Restaurant;
/*
    You need to pass the order request AND the handler to start the chain
    This class will be called by OrderService to do these actions:
        - prepareLines() will validate the OrderLineRequested
        - validate() will use Chain of Responsibility Handlers to check Restaurant Availability and Menu Item quantity
        - createPlacedOrder() will create an order with those orderLines
*/
public class OrderVerify {

    // Most of one item a customer can order at once, so a typo like 100000 is refused
    private static final int MAX_QUANTITY_PER_ITEM = 20;

    // The instance of this class will not be initialized
    private OrderVerify(){} 

    // Helper method to throw invalid domain rule violation with custom msg
    private static DomainRuleViolation DomainRuleInvalid(String message) {
        return new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, message);
    }

    // Method that checks the validity of the Id received
    public static void requireId(String id, String field) {
        if (id == null || !id.matches("[1-9][0-9]{0,9}")) {
            throw DomainRuleInvalid(field + " must be a positive integer");
        }

        try {
            Integer.parseInt(id);
        } catch (NumberFormatException exception) {
            throw DomainRuleInvalid(field + " is outside the supported range");
        }
    }

    // Method to verify the validity of quantity recived and returns it as int
    private static int requireQuantity(RequestedOrderLineItem requestedItem) {
        if (requestedItem.getQuantity() == null) {
            throw DomainRuleInvalid("Quantity is required for item " + requestedItem.getMenuItemId());
        }

        int quantity;

        try {
            quantity = requestedItem.getQuantity().intValueExact();
        } catch (ArithmeticException exception) {
            throw DomainRuleInvalid("Quantity for item " + requestedItem.getMenuItemId()+ " is invalid.");
        }

        if (quantity <= 0) {
            throw DomainRuleInvalid("Quantity for item " + requestedItem.getMenuItemId() + " cannot be zero or negative.");
        }

        if (quantity > MAX_QUANTITY_PER_ITEM) {
            throw DomainRuleInvalid("Quantity for item " + requestedItem.getMenuItemId() + " cannot be more than " + MAX_QUANTITY_PER_ITEM + ".");
        }

        return quantity;
    }

    // Method that will create OrderLine objects using the RequetedOrderLineItem
    public static List<OrderLine> prepareLines(Restaurant restaurant, List<RequestedOrderLineItem> requestedItems) {
        if (restaurant == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "Restaurant not found");
        }
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw DomainRuleInvalid("Order must have at least one order line item");
        }

        // Init a hashmap with the restaurant's menuItems and it's IDs
        Map<String, MenuItem> menuIdHashMap = new HashMap<>();
        for (MenuItem item : restaurant.getMenu()) {
            menuIdHashMap.put(item.getId(), item);
        }

        // Create OrderLines array list to populate as we create order lines
        List<OrderLine> ol = new ArrayList<>();

        for (RequestedOrderLineItem requestedItem : requestedItems) {
            if (requestedItem == null) {
                throw DomainRuleInvalid("A requested order line item cannot not be null");
            }

            String menuItemReqId = requestedItem.getMenuItemId();
            requireId(menuItemReqId, "Item ID"); // throws exception if not valid id

            int quantity = requireQuantity(requestedItem);

            MenuItem menuItem = menuIdHashMap.get(menuItemReqId);

            if (menuItem == null) {
                throw DomainRuleInvalid("Menu Item" + menuItemReqId + " does not exist in this restaurant's menu");
            }

            // Names and prices come from the loaded menu.
            ol.add(OrderLine.fromMenuItem(menuItem, quantity));
        }

        return List.copyOf(ol);
    }

    // Calls chain of responsibility handlers to verify orderLines
    public static void validate(OrderRequest request, BaseOrderHandler handler) {
        if (request == null) {
            throw DomainRuleInvalid("Order request is required");
        }
        if (request.getRestaurant() == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "Restaurant not found");
        }
        if (request.getOrderLines() == null || request.getOrderLines().isEmpty()) {
            throw DomainRuleInvalid("An order must contain at least one item");
        }

        Set<String> selectedIds = new HashSet<>();

        for (OrderLine line : request.getOrderLines()) {
            if (line == null) {
                throw DomainRuleInvalid("An order line must not be null");
            }

            if (line.getQuantity() <= 0) {
                throw DomainRuleInvalid("Quantity must be greater than zero");
            }

            if (!selectedIds.add(line.getMenuItemId())) {
                throw DomainRuleInvalid("Duplicate menuItem " + line.getMenuItemId());
            }

            String restaurantId = request.getRestaurant().getId();
            String itemRestaurantId = line.getMenuItem().getRestaurantId();

            if (!restaurantId.equals(itemRestaurantId)) {
                throw DomainRuleInvalid("Menu Item requested " + line.getMenuItemId() + " belongs to another restaurant");
            }
        }

        if (!handler.handle(request)) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "The order could not pass validation");
        }
    }

    // Create order with the validated order lines
    // deliveryLatitude and deliveryLongitude are null when the destination has no coordinates (#233)
    public static Order createPlacedOrder(String customerId, String restaurantId, String instructions, String deliveryAddress, Double deliveryLatitude, Double deliveryLongitude, List<OrderLine> lines) {
        // The frontend blocks a blank address, but the backend must refuse it too in case the request is sent directly
        if (deliveryAddress == null || deliveryAddress.isBlank()) {
            throw DomainRuleInvalid("Delivery address is required");
        }
        if (deliveryAddress.length() > 255) {
            throw DomainRuleInvalid("Delivery address must not exceed 255 characters");
        }
        // Create a new Order and change the transition to
        Order order = new Order(null, customerId, restaurantId, null, OrderStatus.PLACED, instructions, deliveryAddress, deliveryLatitude, deliveryLongitude, null, lines);

        order.transitionTo(new PlacedState());

        return order;
    }

}
