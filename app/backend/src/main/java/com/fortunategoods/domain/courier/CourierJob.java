package com.fortunategoods.domain.courier;

import java.time.LocalDateTime;
import java.util.List;

public record CourierJob(
    String orderId,
    String status,              // the ORDER's status, e.g. READY or PICKED_UP
    String restaurantName,
    String pickupAddress,
    Double pickupLatitude,
    Double pickupLongitude,
    String destinationName,
    String deliveryAddress,
    Double deliveryLatitude,
    Double deliveryLongitude,
    List<Line> lines,
    String instructions,
    LocalDateTime pickedUpAt
) {
    public record Line(String name, int quantity) {}
}