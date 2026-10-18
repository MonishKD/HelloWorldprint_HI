package com.fortunategoods.application.ord.orders.placement;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.ord.orders.OrderService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.application.ord.orders.OrderServiceResult;
import com.fortunategoods.domain.order.validation.RequestedOrderLineItem;
import com.fortunategoods.domain.user.UserRole;

/*
    Concrete command class for placeOrder(). Valid roles are: USER. execute() will return
    a PlaceOrderResponse request to the frontend.
*/
public class PlaceOrderCommand implements Command<PlaceOrderResponse>{
    private final OrderService service;
    private final Session session;
    private final String restaurantId;
    private final List<RequestedOrderLineItem> requestedItems;
    private final String instructions;
    private final String deliveryAddress;

    public PlaceOrderCommand(OrderService service, Session session, String restaurantId, List<RequestedOrderLineItem> requestedItems, String instructions, String deliveryAddress) {
        this.service = service;
        this.session = session;
        this.restaurantId = restaurantId;
        this.requestedItems = (requestedItems == null) ? null: Collections.unmodifiableList(new ArrayList<>(requestedItems));
        this.instructions = instructions;
        this.deliveryAddress = deliveryAddress;
    }

    @Override
    public Set<UserRole> validRoles() {
        return OrdModulePermissions.placeOrderRoles();
    }

    @Override
    public CommandSuccess<PlaceOrderResponse> execute() {
        OrderServiceResult result = service.placeOrder(String.valueOf(session.getUserID()), restaurantId, requestedItems, instructions, deliveryAddress);

        // Build the response
        PlaceOrderResponse response = new PlaceOrderResponse(result.getOrderId(),result.getStatus());
        return new CommandSuccess<>(response,"Order placed.",result.getEventId());
    }
}
