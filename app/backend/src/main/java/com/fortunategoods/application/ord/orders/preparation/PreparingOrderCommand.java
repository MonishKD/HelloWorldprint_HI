package com.fortunategoods.application.ord.orders.preparation;

import java.util.Set;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.ord.orders.OrderService;
import com.fortunategoods.application.ord.orders.OrderServiceResult;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.user.UserRole;

public class PreparingOrderCommand implements Command<PrepareOrderResponse> {
    private final OrderService orderService;
    private final Session session;
    private final String orderId;

    public PreparingOrderCommand(OrderService orderService, Session session, String orderId){
        this.orderService = orderService;
        this.session = session;
        this.orderId = orderId;
    }


    @Override   
    public Set<UserRole> validRoles(){
        return OrdModulePermissions.preparingOrderRoles();

    }

    @Override
    public CommandSuccess<PrepareOrderResponse> execute() {
        OrderServiceResult result = orderService.prepareOrder(String.valueOf(session.getUserID()), session.getRole(), orderId);
        PrepareOrderResponse response = new PrepareOrderResponse(result.getOrderId(), result.getStatus());
        return new CommandSuccess<>(response, "Order is now being prepared!", result.getEventId());
    }



}
