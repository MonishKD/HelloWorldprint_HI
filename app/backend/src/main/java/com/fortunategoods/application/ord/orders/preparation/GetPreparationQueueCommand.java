package com.fortunategoods.application.ord.orders.preparation;


import java.util.List;
import java.util.Set;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.ord.orders.RestaurantOrderService;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.user.UserRole;


public class GetPreparationQueueCommand implements Command<List<GetPreparationQueueResponse>> {
    private final RestaurantOrderService service;
    private final Session session;
    private final String restaurantId;

    public GetPreparationQueueCommand(RestaurantOrderService service, Session session, String restaurantId) {
        this.service = service;
        this.session = session;
        this.restaurantId = restaurantId;
    }

    @Override
    public Set<UserRole> validRoles() {
        return OrdModulePermissions.viewPreparationQueueRoles();
    }

    @Override
    public CommandSuccess<List<GetPreparationQueueResponse>> execute() {
        List<GetPreparationQueueResponse> queue = service.getPreparationQueue(String.valueOf(session.getUserID()), session.getRole(), restaurantId);
        return CommandSuccess.success(queue, "Preparation queue loaded.");
    }
}
