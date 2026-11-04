package com.fortunategoods.domain.pricing;

import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.pricing.strategy.DeliveryFeeRule;
import com.fortunategoods.domain.pricing.strategy.PricingRule;
import com.fortunategoods.domain.pricing.strategy.ServiceFeeRule;
import com.fortunategoods.domain.pricing.strategy.SmallOrderSurchargeRule;

import java.util.List;

public class FareCalculator {
    private final PricingRule delivery;
    private final PricingRule service;
    private final PricingRule surcharge;
    public FareCalculator(long deliveryFeeCents, int serviceFeePercent, long thresholdCents, long surchargeCents) {
        this.delivery = new DeliveryFeeRule(deliveryFeeCents);
        this.service = new ServiceFeeRule(serviceFeePercent);
        this.surcharge = new SmallOrderSurchargeRule(thresholdCents, surchargeCents);
    }

    public Fare calculate(List<OrderLine> lines){
        long amount = 0;
        for (OrderLine line : lines){
            amount+= (line.getPrice()*100)+line.getQuantity();
        }
        long deliveryFee = delivery.compute(amount);
        long serviceFee = service.compute(amount);
        long surchargeFee = surcharge.compute(amount);

        return new Fare(amount,deliveryFee,serviceFee,surchargeFee);
    }

}
