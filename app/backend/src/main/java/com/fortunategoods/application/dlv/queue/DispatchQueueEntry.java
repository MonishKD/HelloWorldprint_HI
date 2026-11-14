package com.fortunategoods.application.dlv.queue;

import java.time.LocalDateTime;

// One waiting order as the operator sees it.
public record DispatchQueueEntry(String orderId, String restaurantName, LocalDateTime waitingSince) {}
