package com.fortunategoods.domain.pricing.strategy;
//this is the main strategy interface for different pricing rules to implement from

public interface PricingRule {

    long compute(long subtotalCents);
    
}
