package com.fortunategoods.application.prc.checkout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.ord.orders.OrderServiceResult;
import com.fortunategoods.application.ord.orders.placement.PlaceOrderResponse;
import com.fortunategoods.application.prc.PaymentService;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.order.validation.RequestedOrderLineItem;
import com.fortunategoods.domain.user.UserRole;

/*
    Concrete command for placing an order paid by card (#259). Valid roles: CUSTOMER.
    Same as PlaceOrderCommand, plus the payment started at checkout: the order is placed only if that payment
    is held for exactly the order's fare. Returns the same PlaceOrderResponse as a normal placement.
*/
public class PlacePaidOrderCommand implements Command<PlaceOrderResponse> {
    private final PaymentService service;
    private final Session session;
    private final String restaurantId;
    private final List<RequestedOrderLineItem> requestedItems;
    private final String instructions;
    private final String deliveryAddress;
    private final String paymentId;

    public PlacePaidOrderCommand(PaymentService service, Session session, String restaurantId, List<RequestedOrderLineItem> requestedItems,
            String instructions, String deliveryAddress, String paymentId) {
        this.service = service;
        this.session = session;
        this.restaurantId = restaurantId;
        this.requestedItems = (requestedItems == null) ? null : Collections.unmodifiableList(new ArrayList<>(requestedItems));
        this.instructions = instructions;
        this.deliveryAddress = deliveryAddress;
        this.paymentId = paymentId;
    }

    @Override
    public Set<UserRole> validRoles() {
        return OrdModulePermissions.placeOrderRoles();
    }

    @Override
    public CommandSuccess<PlaceOrderResponse> execute() {
        OrderServiceResult result = service.placePaidOrder(String.valueOf(session.getUserID()), restaurantId, requestedItems,
                instructions, deliveryAddress, paymentId);

        PlaceOrderResponse response = new PlaceOrderResponse(result.getOrderId(), result.getStatus());
        return new CommandSuccess<>(response, "Order placed. The card payment is held and will be charged on delivery.", result.getEventId());
    }
}
