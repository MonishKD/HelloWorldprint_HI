package com.fortunategoods.application.prc.summary;

import java.time.LocalDateTime;
import java.util.List;

/*
    Class used to send a delivered order's summary to the frontend (#262, R-PRC-02).
    The fare parts and the total come from the order's CHARGE ledger entry, so the total is always
    what was charged (#261). Money is in cents, like the checkout (#259) and the history (#264).
    cardBrand and cardLast4 are empty for an order placed without card payment.
*/
public class OrderSummaryResponse {
    private final String orderId;
    private final List<OrderSummaryLineResponse> lines;
    private final long subtotalCents;
    private final long deliveryFeeCents;
    private final long serviceFeeCents;
    private final long surchargeCents;
    private final long totalCents;
    private final LocalDateTime placedAt;
    private final LocalDateTime deliveredAt;
    private final String restaurantName;
    private final String courierName;
    private final String deliveryAddress;
    private final String cardBrand;
    private final String cardLast4;

    public OrderSummaryResponse(String orderId, List<OrderSummaryLineResponse> lines, long subtotalCents, long deliveryFeeCents,
            long serviceFeeCents, long surchargeCents, long totalCents, LocalDateTime placedAt, LocalDateTime deliveredAt,
            String restaurantName, String courierName, String deliveryAddress, String cardBrand, String cardLast4) {
        this.orderId = orderId;
        this.lines = lines;
        this.subtotalCents = subtotalCents;
        this.deliveryFeeCents = deliveryFeeCents;
        this.serviceFeeCents = serviceFeeCents;
        this.surchargeCents = surchargeCents;
        this.totalCents = totalCents;
        this.placedAt = placedAt;
        this.deliveredAt = deliveredAt;
        this.restaurantName = restaurantName;
        this.courierName = courierName;
        this.deliveryAddress = deliveryAddress;
        this.cardBrand = cardBrand;
        this.cardLast4 = cardLast4;
    }

    public String getOrderId() {
        return orderId;
    }

    public List<OrderSummaryLineResponse> getLines() {
        return lines;
    }

    public long getSubtotalCents() {
        return subtotalCents;
    }

    public long getDeliveryFeeCents() {
        return deliveryFeeCents;
    }

    public long getServiceFeeCents() {
        return serviceFeeCents;
    }

    public long getSurchargeCents() {
        return surchargeCents;
    }

    public long getTotalCents() {
        return totalCents;
    }

    public LocalDateTime getPlacedAt() {
        return placedAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public String getRestaurantName() {
        return restaurantName;
    }

    public String getCourierName() {
        return courierName;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public String getCardBrand() {
        return cardBrand;
    }

    public String getCardLast4() {
        return cardLast4;
    }
}
