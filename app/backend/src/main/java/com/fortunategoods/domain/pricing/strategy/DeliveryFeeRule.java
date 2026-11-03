package com.fortunategoods.domain.pricing.strategy;
//for this one, just return the delivery fee
public class DeliveryFeeRule implements PricingRule {
    private final long feeCents;

    public DeliveryFeeRule(long feeCents) {
        this.feeCents = feeCents;
    }

    @Override
    public long compute(long subtotalCents) {
        return feeCents;
    }
}
