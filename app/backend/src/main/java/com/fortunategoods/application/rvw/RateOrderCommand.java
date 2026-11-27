package com.fortunategoods.application.rvw;

import java.util.Set;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.rating.RvwModulePermissions;
import com.fortunategoods.domain.user.UserRole;

// Command for a customer to rate their delivered order (R-RVW-01). RatingService is the receiver.
// Run through CommandInvoker so it lands in the command log. Who rates comes from the session.
public final class RateOrderCommand implements Command<RatingResponse> {

    private final RatingService service;
    private final Session session;
    private final String orderId;
    private final Integer restaurantStars;
    private final Integer courierStars;

    public RateOrderCommand(RatingService service, Session session, String orderId, Integer restaurantStars,
            Integer courierStars) {
        this.service = service;
        this.session = session;
        this.orderId = orderId;
        this.restaurantStars = restaurantStars;
        this.courierStars = courierStars;
    }

    @Override
    public Set<UserRole> validRoles() {
        return RvwModulePermissions.rateOrderRoles();
    }

    @Override
    public CommandSuccess<RatingResponse> execute() {
        RatingResponse response = service.rateOrder(String.valueOf(session.getUserID()), orderId, restaurantStars, courierStars);
        return CommandSuccess.success(response, "Order rated.");
    }
}
