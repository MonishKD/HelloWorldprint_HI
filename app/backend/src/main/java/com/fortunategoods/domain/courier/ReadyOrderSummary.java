package com.fortunategoods.domain.courier;

import java.time.LocalDateTime;

// One ready order in the courier's overview (view only, no customer or payment fields).
public record ReadyOrderSummary(
    String orderId,
    String restaurantName,
    String pickupAddress,
    int itemCount,
    LocalDateTime readyAt
) {}