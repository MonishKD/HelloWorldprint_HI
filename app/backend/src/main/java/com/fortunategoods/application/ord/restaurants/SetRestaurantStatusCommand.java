package com.fortunategoods.application.ord.restaurants;

import java.util.Locale;
import java.util.Set;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.domain.user.UserRole;

public class SetRestaurantStatusCommand implements Command<SetRestaurantStatusResponse> {

    private final RestaurantService service;
    private final Session session;
    private final String restaurantId;
    private final String requestedStatus;

    public SetRestaurantStatusCommand(RestaurantService service, Session session, String restaurantId, String requestedStatus) {
        this.service = service;
        this.session = session;
        this.restaurantId = restaurantId;
        this.requestedStatus = requestedStatus;
    }

    @Override
    public Set<UserRole> validRoles() {
        return OrdModulePermissions.setRestaurantStatusRoles();
    }

    @Override
    public CommandSuccess<SetRestaurantStatusResponse> execute() {
        RestaurantStatus status = parseStatus();

        Restaurant restaurant = service.setRestaurantStatus(String.valueOf(session.getUserID()), session.getRole(), restaurantId, status);

        SetRestaurantStatusResponse response = new SetRestaurantStatusResponse(restaurant.getId(), restaurant.getStatus());

        return CommandSuccess.success(response, "Restaurant status updated.");
    }

    // Parses the status to check validity
    private RestaurantStatus parseStatus() {
        if (requestedStatus == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID,"Restaurant status is required");
        }
        try {
            return RestaurantStatus.valueOf(requestedStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Status must be open, paused or closed");
        }
    }
}