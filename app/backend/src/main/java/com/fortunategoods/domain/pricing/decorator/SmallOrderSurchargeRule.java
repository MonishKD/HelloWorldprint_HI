package com.fortunategoods.domain.pricing.decorator;

public class SmallOrderSurchargeRule extends PricingRuleDecorator {
    private final long thresholdCents;
    private final long surchargeCents;

    public SmallOrderSurchargeRule(PricingRule wrappee, long thresholdCents, long surchargeCents){
        super(wrappee);
        this.thresholdCents = thresholdCents;
        this.surchargeCents = surchargeCents;

    }

    @Override
    public long compute(long subtotalCents){
        return super.compute(subtotalCents) + extra(subtotalCents);
    }

    private long extra(long subtotalCents){
        if(subtotalCents<thresholdCents){
            return surchargeCents;
        }
        return 0;
    }
}
