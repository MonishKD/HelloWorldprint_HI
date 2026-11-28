package com.fortunategoods.application.party.gathering;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.party.PartyCommand;
import com.fortunategoods.application.party.PartyResponse;
import com.fortunategoods.application.party.PartyService;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.validation.RequestedOrderLineItem;

// The host turns their cart into a party: the cart becomes the host's own items, and the party gets its code.
public class StartPartyCommand extends PartyCommand<PartyResponse> {
    private final String restaurantId;
    private final List<RequestedOrderLineItem> cartItems;
    private final String instructions;
    private final String deliveryAddress;

    public StartPartyCommand(PartyService service, Session session, String restaurantId,
            List<RequestedOrderLineItem> cartItems, String instructions, String deliveryAddress) {
        super(service, session);
        this.restaurantId = restaurantId;
        this.cartItems = (cartItems == null) ? null : Collections.unmodifiableList(new ArrayList<>(cartItems));
        this.instructions = instructions;
        this.deliveryAddress = deliveryAddress;
    }

    @Override
    public CommandSuccess<PartyResponse> execute() {
        PartyResponse party = service.start(customerId(), restaurantId, cartItems, instructions, deliveryAddress);
        return CommandSuccess.success(party, "Party " + party.code() + " started at " + party.restaurantName() + ".");
    }
}
