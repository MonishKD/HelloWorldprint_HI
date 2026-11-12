package com.fortunategoods.domain.pricing.decorator;

//conctrete component
public class BaseFare implements PricingRule {
    //simply return 0 since its a simple fare with no extra fees
    //the rest of the fees will come from the decorators
    @Override 
    public long compute(long subtotalCents){
        return 0;
    }
    
}
