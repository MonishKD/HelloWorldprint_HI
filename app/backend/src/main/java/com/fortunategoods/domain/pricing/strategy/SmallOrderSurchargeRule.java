package com.fortunategoods.domain.pricing.strategy;

public class SmallOrderSurchargeRule implements PricingRule{
    private final long thresholdCents;
    private final long surchargeCents;

    public SmallOrderSurchargeRule(long thresholdCents, long surchargeCents){
        this.thresholdCents = thresholdCents;
        this.surchargeCents = surchargeCents;

    }

    public long compute(long subtotalCents){
        if(subtotalCents<thresholdCents){
            return surchargeCents;
        }
        return 0;
    }
}
