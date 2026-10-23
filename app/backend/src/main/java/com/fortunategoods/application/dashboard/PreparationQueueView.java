package com.fortunategoods.application.dashboard;

import java.util.List;

import com.fortunategoods.application.ord.orders.preparation.GetPreparationQueueResponse;

/*
    The restaurant dashboard: the staff member's own restaurant and its preparation queue.
    Queue slots are { position, orderId, status }, position 1 is prepared next.
    Statuses are lowercase ("open", "accepted"), like the other order responses.
*/
public record PreparationQueueView(String restaurantId, String restaurantName, String restaurantStatus,
        List<GetPreparationQueueResponse> queue) {

    public PreparationQueueView {
        queue = List.copyOf(queue);
    }
}
