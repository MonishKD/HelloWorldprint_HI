package com.fortunategoods.application.prc.summary;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/*
    Class used to send a delivered order's summary to the frontend (#262, R-PRC-02).
    The fare parts and the total come from the order's CHARGE ledger entry, so the total is always
    what was charged (#261). Money is in dollars with 2 decimals, like the item prices in lines.
    cardBrand and cardLast4 are empty for an order placed without card payment.
*/
public class OrderSummaryResponse {
    private final String orderId;
    private final List<OrderSummaryLineResponse> lines;
    private final BigDecimal subtotal;
    private final BigDecimal deliveryFee;
    private final BigDecimal serviceFee;
    private final BigDecimal surcharge;
    private final BigDecimal total;
    private final LocalDateTime placedAt;
    private final LocalDateTime deliveredAt;
    private final String restaurantName;
    private final String courierName;
    private final String deliveryAddress;
    private final String cardBrand;
    private final String cardLast4;

    public OrderSummaryResponse(String orderId, List<OrderSummaryLineResponse> lines, BigDecimal subtotal, BigDecimal deliveryFee,
            BigDecimal serviceFee, BigDecimal surcharge, BigDecimal total, LocalDateTime placedAt, LocalDateTime deliveredAt,
            String restaurantName, String courierName, String deliveryAddress, String cardBrand, String cardLast4) {
        this.orderId = orderId;
        this.lines = lines;
        this.subtotal = subtotal;
        this.deliveryFee = deliveryFee;
        this.serviceFee = serviceFee;
        this.surcharge = surcharge;
        this.total = total;
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

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDeliveryFee() {
        return deliveryFee;
    }

    public BigDecimal getServiceFee() {
        return serviceFee;
    }

    public BigDecimal getSurcharge() {
        return surcharge;
    }

    public BigDecimal getTotal() {
        return total;
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
