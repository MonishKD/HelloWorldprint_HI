package com.fortunategoods.application.dashboard;

import java.time.LocalDateTime;

// How an order's delivery is going. Each field is null until it is known.
public record OrderDelivery(String courierName, LocalDateTime pickedUpAt, LocalDateTime deliveredAt,
        Double deliveryLat, Double deliveryLng) {

    static final OrderDelivery NONE = new OrderDelivery(null, null, null, null, null);
}
