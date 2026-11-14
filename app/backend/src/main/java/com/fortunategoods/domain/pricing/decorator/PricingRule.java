package com.fortunategoods.domain.pricing.decorator;

//Component interface
public interface PricingRule {
    long compute(long subtotalCents);
}