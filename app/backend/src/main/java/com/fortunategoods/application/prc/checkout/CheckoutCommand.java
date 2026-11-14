package com.fortunategoods.application.prc.checkout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.prc.PaymentService;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.order.validation.RequestedOrderLineItem;
import com.fortunategoods.domain.user.UserRole;

/*
    Concrete command for starting a card payment at checkout (#259). Valid roles are the ones that can place
    an order (CUSTOMER). execute() prices the cart and holds that amount on the card, nothing is placed yet.
*/
public class CheckoutCommand implements Command<CheckoutResponse> {
    private final PaymentService service;
    private final String restaurantId;
    private final List<RequestedOrderLineItem> requestedItems;

    public CheckoutCommand(PaymentService service, String restaurantId, List<RequestedOrderLineItem> requestedItems) {
        this.service = service;
        this.restaurantId = restaurantId;
        this.requestedItems = (requestedItems == null) ? null : Collections.unmodifiableList(new ArrayList<>(requestedItems));
    }

    @Override
    public Set<UserRole> validRoles() {
        return OrdModulePermissions.placeOrderRoles();
    }

    @Override
    public CommandSuccess<CheckoutResponse> execute() {
        CheckoutResponse response = service.startCheckout(restaurantId, requestedItems);
        String total = String.format(Locale.ROOT, "%.2f", response.getTotalCents() / 100.0);
        return CommandSuccess.success(response, "Checkout started: $" + total + " will be held on the card when it is confirmed.");
    }
}
