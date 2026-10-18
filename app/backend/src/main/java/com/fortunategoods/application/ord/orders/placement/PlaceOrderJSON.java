package com.fortunategoods.application.ord.orders.placement;

import java.util.ArrayList;
import java.util.List;

import com.fortunategoods.domain.order.validation.RequestedOrderLineItem;

/*
    This class will be used to process the full order JSON request.
    It will create RequestedOrderLineItems for each order lines using
    PlaceOrderLinesJSON class objects.
*/
public class PlaceOrderJSON {
    private String restaurantId;
    private List<PlaceOrderLinesJSON> ol;
    private String instructions;
    private String deliveryAddress;

    public PlaceOrderJSON(){}

    public PlaceOrderJSON(String restaurantId, List<PlaceOrderLinesJSON> ol, String instructions, String deliveryAddress) {
        this.restaurantId = restaurantId;
        this.ol = ol == null ? null : new ArrayList<>(ol);
        this.instructions = instructions;
        this.deliveryAddress = deliveryAddress;
    }

    public String getRestaurantId() {
        return restaurantId;
    }

    public void setRestaurantId(String restaurantId) {
        this.restaurantId = restaurantId;
    }

    public List<PlaceOrderLinesJSON> getLines() {
        return ol;
    }

    public void setLines(List<PlaceOrderLinesJSON> ol) {
        this.ol = ol;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    // Method to convert each PlaceOrderLinesJSON into RequestedOrderLineItem
    public List<RequestedOrderLineItem> toRequestedItems() {
        if (ol == null) {
            return null;
        }

        List<RequestedOrderLineItem> requestedItems = new ArrayList<>();
        for (PlaceOrderLinesJSON line : ol) {
            if (line == null) {
                requestedItems.add(null);
            } else {
                requestedItems.add(new RequestedOrderLineItem(line.getMenuItemId(), line.getQuantity()));
            }
        }

        return requestedItems;
    }
}
