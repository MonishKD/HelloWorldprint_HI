package com.fortunategoods.application.ord.orders.pending;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.ord.orders.RestaurantOrderService;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.user.UserRole;

public class GetPendingOrdersCommand implements Command<List<PendingOrderResponse>> {
    private final RestaurantOrderService service;
    private final Session session;
    private final String restaurantId;
    private final String status;

    public GetPendingOrdersCommand(RestaurantOrderService service, Session session, String restaurantId, String status){
        this.service = service;
        this.session = session;
        this.restaurantId = restaurantId;
        this.status = status;
    }

    @Override
    public Set<UserRole> validRoles(){
        return OrdModulePermissions.viewPlacedOrdersRoles();
    }

    @Override
    public CommandSuccess<List<PendingOrderResponse>> execute() {
        if (!"placed".equals(status)) { // HTTP will use ?status=placed
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Only Orders with PLACED status can run this command.");
        }

        // Get a list of all pending orders
        List<Order> orders = service.getPendingOrders(String.valueOf(session.getUserID()), session.getRole(), restaurantId);

        List<PendingOrderResponse> responses = new ArrayList<>();

        for (Order order : orders) {
            responses.add(new PendingOrderResponse(order));
        }

        // Since this is not a state transition event, and we are only fetching the PLACED orders
        // This will not have an eventId, or record an OrderEvent
        return CommandSuccess.success(List.copyOf(responses),"Pending orders loaded.");
    
    }

}
