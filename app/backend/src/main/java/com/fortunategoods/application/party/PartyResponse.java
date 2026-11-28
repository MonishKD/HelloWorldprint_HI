package com.fortunategoods.application.party;

import java.util.List;

// The whole party as the party room shows it (GET /api/parties/{code} and every party action answer with it).
// Money is in cents like the other pricing answers (checkout, summary, history).
// status: GATHERING, PAYING, PLACED or CANCELLED. mode, roulettePayerId and orderId are null until they are known.
// orderStatus is the placed order's status (PLACED, ACCEPTED, ..., DELIVERED, REJECTED...), cancelReason why it was cancelled.
public record PartyResponse(String code, String restaurantId, String restaurantName, String hostId, String deliveryAddress,
        String status, String mode, String roulettePayerId, String orderId, String orderStatus, String cancelReason,
        List<PartyMemberResponse> members,
        List<PartyLineResponse> lines, long subtotalCents, long deliveryFeeCents, long serviceFeeCents,
        long surchargeCents, long totalCents) {}
