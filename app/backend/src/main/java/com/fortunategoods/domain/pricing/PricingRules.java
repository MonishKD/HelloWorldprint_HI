package com.fortunategoods.domain.pricing;

// The four pricing values from seed-config.json (#234), read once at startup and never changed while the app runs.
// No fee or threshold is written in the code, they all come from here (PH3 5.4).
// The service fee is a whole percent of the subtotal (10 means 10%), the others are amounts in cents.
public class PricingRules {
    private final long deliveryFeeCents;
    private final int serviceFeePercent;
    private final long smallOrderThresholdCents;
    private final long smallOrderSurchargeCents;

    public PricingRules(long deliveryFeeCents, int serviceFeePercent, long smallOrderThresholdCents, long smallOrderSurchargeCents) {
        this.deliveryFeeCents = deliveryFeeCents;
        this.serviceFeePercent = serviceFeePercent;
        this.smallOrderThresholdCents = smallOrderThresholdCents;
        this.smallOrderSurchargeCents = smallOrderSurchargeCents;
    }

    public long getDeliveryFeeCents() {
        return deliveryFeeCents;
    }

    public int getServiceFeePercent() {
        return serviceFeePercent;
    }

    public long getSmallOrderThresholdCents() {
        return smallOrderThresholdCents;
    }

    public long getSmallOrderSurchargeCents() {
        return smallOrderSurchargeCents;
    }
}
