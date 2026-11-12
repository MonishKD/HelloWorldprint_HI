package com.fortunategoods.domain.pricing.decorator;

public class DeliveryFeeRule extends PricingRuleDecorator {
    private final long feeCents;

    public DeliveryFeeRule(PricingRule wrappee, long feeCents) {
        super(wrappee);
        this.feeCents = feeCents;
    }

    @Override
    public long compute(long subtotalCents) {
        return super.compute(subtotalCents) + extra(subtotalCents);
    }

    private long extra(long subtotalCents) {
        return feeCents;
    }
}
