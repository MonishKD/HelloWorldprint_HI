package com.fortunategoods.application.prc.history;

import java.time.LocalDateTime;

import com.fortunategoods.domain.pricing.LedgerEntry;

/*
    One ledger entry in a customer's, restaurant's or operator's history (#264).
    Same fields as LedgerEntry, plus the restaurant's name. Money is in cents, like the checkout (#259).
    entryType is CHARGE, NO_CHARGE or REFUND. A refunded order shows its CHARGE row and its REFUND row.
*/
public class LedgerHistoryResponse extends HistoryResponse {
    private final String entryType;
    private final String finalStatus;
    private final long subtotalCents;
    private final long deliveryFeeCents;
    private final long serviceFeeCents;
    private final long surchargeCents;
    private final long amountCents;
    private final LocalDateTime placedAt;
    private final LocalDateTime finishedAt;

    public LedgerHistoryResponse(LedgerEntry entry, String restaurantName) {
        super(entry.getOrderId(), restaurantName);
        this.entryType = entry.getType().name();
        this.finalStatus = entry.getFinalStatus().name();
        this.subtotalCents = entry.getSubtotalCents();
        this.deliveryFeeCents = entry.getDeliveryFeeCents();
        this.serviceFeeCents = entry.getServiceFeeCents();
        this.surchargeCents = entry.getSurchargeCents();
        this.amountCents = entry.getAmountCents();
        this.placedAt = entry.getPlacedAt();
        this.finishedAt = entry.getFinishedAt();
    }

    public String getEntryType() {
        return entryType;
    }

    public String getFinalStatus() {
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
}
