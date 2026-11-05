package com.fortunategoods.application.prc.history;

import java.time.LocalDateTime;

/*
    One delivery in a courier's history (#264): { orderId, restaurantName, destinationName, deliveredAt }.
    These are the fields phase3-frontend's CourierDelivery reads. No money field, on purpose (Appendix A).
    destinationName is who the order went to: the customer's name.
*/
public class CourierHistoryResponse extends HistoryResponse {
    private final String destinationName;
    private final LocalDateTime deliveredAt;

    public CourierHistoryResponse(String orderId, String restaurantName, String destinationName, LocalDateTime deliveredAt) {
        super(orderId, restaurantName);
        this.destinationName = destinationName;
        this.deliveredAt = deliveredAt;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }
}
