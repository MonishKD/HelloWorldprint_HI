package com.fortunategoods.domain.pricing;

import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.pricing.decorator.DeliveryFeeRule;
import com.fortunategoods.domain.pricing.decorator.PricingRule;
import com.fortunategoods.domain.pricing.decorator.ServiceFeeRule;
import com.fortunategoods.domain.pricing.decorator.SmallOrderSurchargeRule;
import com.fortunategoods.domain.pricing.decorator.BaseFare;

import java.util.List;

public class FareCalculator {
    private final PricingRule delivery;
    private final PricingRule service;
    private final PricingRule surcharge;

    public FareCalculator(long deliveryFeeCents, int serviceFeePercent, long thresholdCents, long surchargeCents) {
        this.delivery = new DeliveryFeeRule(new BaseFare(), deliveryFeeCents);
        this.service = new ServiceFeeRule(delivery, serviceFeePercent);
        this.surcharge = new SmallOrderSurchargeRule(service, thresholdCents, surchargeCents);
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

        long untilDelivery = delivery.compute(amount);
        long untilService = service.compute(amount);
        long untilSurcharge = surcharge.compute(amount);
        //gets the separate extra fees per step
        long deliveryFee = untilDelivery;
        long serviceFee = untilService - untilDelivery;
        long surchargeFee = untilSurcharge - untilService;

        return new Fare(amount,deliveryFee,serviceFee,surchargeFee);
    }

}
