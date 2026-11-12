package com.fortunategoods.domain.pricing.decorator;


//base decorator
public class PricingRuleDecorator implements PricingRule {
    private final PricingRule wrappee;

    public PricingRuleDecorator(PricingRule wrappee) {
        this.wrappee = wrappee;
    }

    @Override 
    public long compute(long subtotalCents) {
        return wrappee.compute(subtotalCents);
    }
}
