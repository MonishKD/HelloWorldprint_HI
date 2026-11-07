package com.fortunategoods.domain.pricing;

import java.time.LocalDateTime;

import com.fortunategoods.domain.order.OrderStatus;

// One row of the ledger (#223, R-PRC-03): how a finished order ended and what it cost. Entries are never edited.
public class LedgerEntry {
    // CHARGE: delivered and paid. NO_CHARGE: rejected or cancelled.
    // REFUND: the operator's refund (#266), a new entry, the CHARGE entry is never changed.
    public enum Type { CHARGE, NO_CHARGE, REFUND }

    private final String orderId;
    private final Type type;
    private final OrderStatus finalStatus;
    // The fare's parts, all 0 for NO_CHARGE
    private final long subtotalCents;
    private final long deliveryFeeCents;
    private final long serviceFeeCents;
    private final long surchargeCents;
    private final long amountCents;
    private final LocalDateTime placedAt;
    private final LocalDateTime finishedAt;
    // The Stripe payment id (#259), empty when nothing was charged
    private final String paymentReference;

    public LedgerEntry(String orderId, Type type, OrderStatus finalStatus, long subtotalCents, long deliveryFeeCents,
            long serviceFeeCents, long surchargeCents, long amountCents, LocalDateTime placedAt, LocalDateTime finishedAt,
            String paymentReference) {
        this.orderId = orderId;
        this.type = type;
        this.finalStatus = finalStatus;
        this.subtotalCents = subtotalCents;
        this.deliveryFeeCents = deliveryFeeCents;
        this.serviceFeeCents = serviceFeeCents;
        this.surchargeCents = surchargeCents;
        this.amountCents = amountCents;
        this.placedAt = placedAt;
        this.finishedAt = finishedAt;
        this.paymentReference = paymentReference;
    }

    // Delivered: the fare's parts, the placed and delivered times and the Stripe reference
    public static LedgerEntry charge(String orderId, Fare fare, LocalDateTime placedAt, LocalDateTime deliveredAt, String paymentReference) {
        return new LedgerEntry(orderId, Type.CHARGE, OrderStatus.DELIVERED, fare.getSubtotalCents(), fare.getDeliveryFeeCents(),
                fare.getServiceFeeCents(), fare.getSurchargeCents(), fare.getTotalCents(), placedAt, deliveredAt, paymentReference);
    }

    //0 so no charge, but the final state and times are kept so the ledger covers every order
    public static LedgerEntry noCharge(String orderId, OrderStatus finalStatus, LocalDateTime placedAt, LocalDateTime finishedAt) {
        return new LedgerEntry(orderId, Type.NO_CHARGE, finalStatus, 0, 0, 0, 0, 0, placedAt, finishedAt, null);
    }

    // Same fare as the charge it gives back, with the refund's time. The amounts stay positive: the type says the money went back.
    public static LedgerEntry refund(LedgerEntry charge, LocalDateTime refundedAt) {
        return new LedgerEntry(charge.orderId, Type.REFUND, OrderStatus.REFUNDED, charge.subtotalCents, charge.deliveryFeeCents,
                charge.serviceFeeCents, charge.surchargeCents, charge.amountCents, charge.placedAt, refundedAt, charge.paymentReference);
    }

    public String getOrderId() {
        return orderId;
    }

    public Type getType() {
        return type;
    }

    public OrderStatus getFinalStatus() {
        return finalStatus;
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

    public long getAmountCents() {
        return amountCents;
    }

    public LocalDateTime getPlacedAt() {
        return placedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public String getPaymentReference() {
        return paymentReference;
    }
}
