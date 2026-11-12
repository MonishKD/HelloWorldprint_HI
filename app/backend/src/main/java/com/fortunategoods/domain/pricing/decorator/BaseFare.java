package com.fortunategoods.domain.pricing.decorator;

//conctrete component
public class BaseFare implements PricingRule {
    //return 0 to ensure its wrong
    @Override 
    public long compute(long subtotalCents){
        return 0;
    }
    
}
