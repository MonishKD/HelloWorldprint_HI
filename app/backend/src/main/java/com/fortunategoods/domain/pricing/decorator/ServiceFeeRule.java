package com.fortunategoods.domain.pricing.decorator;

public class ServiceFeeRule extends PricingRuleDecorator {

    private final int percent;
    public ServiceFeeRule(PricingRule wrappee, int percent){
        super(wrappee);
        this.percent = percent;
    }

    @Override 
    public long compute(long subtotalCents){
        return super.compute(subtotalCents) + extra(subtotalCents);
    }

    private long extra(long subtotalCents){
        //a bit messy but works

        return (long)Math.round(subtotalCents*(percent*0.01));
    }
}
