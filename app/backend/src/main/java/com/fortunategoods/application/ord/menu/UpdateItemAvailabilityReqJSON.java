package com.fortunategoods.application.ord.menu;

import com.fortunategoods.domain.restaurant.MenuItemStatus;

/*
    The body the frontend sends to PATCH /api/restaurants/{restaurantId}/menu/{itemId}/availability (#89).
    Example: { "status": "sold_out" }
    Spring (Jackson) fills "status" from the JSON body through the setter below.
*/
public class UpdateItemAvailabilityReqJSON {
    private String status; // "available" or "sold_out", the same words the frontend uses

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    // Turns the text into a MenuItemStatus. Returns null when the text is missing or is neither of the two words.
    // The null is not refused here: MenuService.updateItemAvailability refuses it with INVALID (400),
    // so the refusal goes through CommandInvoker and is written in the command log like every other refusal.
    public MenuItemStatus toStatus() {
        if ("available".equalsIgnoreCase(status)) {
            return MenuItemStatus.AVAILABLE;
        }
        if ("sold_out".equalsIgnoreCase(status)) {
            return MenuItemStatus.SOLD_OUT;
        }
        return null;
    }
}
