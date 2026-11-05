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

    // The way the app builds it: with the configured values from seed-config.json (#234)
    public FareCalculator(PricingRules rules) {
        this(rules.getDeliveryFeeCents(), rules.getServiceFeePercent(), rules.getSmallOrderThresholdCents(), rules.getSmallOrderSurchargeCents());
    }

    // Works on order lines and on cart lines (built the same way by OrderVerify.prepareLines), so the payment
    // amount is known before the order exists (#222)
    public Fare calculate(List<OrderLine> lines){
        long amount = 0;
        for (OrderLine line : lines){
            // price is in dollars (29.99): rounded to cents first so it is exactly 2999, then times the quantity
            amount += Math.round(line.getPrice() * 100) * line.getQuantity();
        }
        long deliveryFee = delivery.compute(amount);
        long serviceFee = service.compute(amount);
        long surchargeFee = surcharge.compute(amount);

        return new Fare(amount,deliveryFee,serviceFee,surchargeFee);
    }

}
