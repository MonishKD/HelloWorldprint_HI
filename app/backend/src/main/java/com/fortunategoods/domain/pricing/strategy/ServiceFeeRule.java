package com.fortunategoods.domain.pricing.strategy;

public class ServiceFeeRule implements PricingRule {

    private final int percent;
    public ServiceFeeRule(int percent){
        this.percent = percent;
    }

    @Override 
    public long compute(long subtotalCents){
        //a bit messy but works
        return (long)Math.round(subtotalCents*(percent*0.01));
    }
}
