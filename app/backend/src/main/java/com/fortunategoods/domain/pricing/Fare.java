package com.fortunategoods.domain.pricing;

public class Fare {
    private final long subtotalCents;
    private final long deliveryFeeCents;
    private final long serviceFeeCents;
    private final long surchargeCents;
    private final long totalCents;

    public Fare(long subtotalCents, long deliveryFeeCents, long serviceFeeCents, long surchargeCents) {
        this.subtotalCents = subtotalCents;
        this.deliveryFeeCents = deliveryFeeCents;
        this.serviceFeeCents = serviceFeeCents;
        this.surchargeCents = surchargeCents;
        this.totalCents = subtotalCents + deliveryFeeCents + serviceFeeCents + surchargeCents;
    }

    //for testing
    @Override 
    public boolean equals(Object o){
        if(this==o){
            return true;
        }
        if(!(o instanceof Fare f)){
            return false;
        }
        return subtotalCents == f.subtotalCents && deliveryFeeCents == f.deliveryFeeCents && serviceFeeCents == f.serviceFeeCents && surchargeCents == f.surchargeCents && totalCents == f.totalCents;
    }

    public long getSubtotalCents() {
        return subtotalCents;
    }

    public long getTotalCents() {
        return totalCents;
    }

    // Old spelling, still used by FareCalculatorTest. New code uses getSurchargeCents().
    public long getSutchargeCents() {
        return surchargeCents;
    }

    public long getSurchargeCents() {
        return surchargeCents;
    }

    // The fare's parts for the checkout breakdown and the ledger (#259, #260, #261)
    public long getDeliveryFeeCents() {
        return deliveryFeeCents;
    }

    public long getServiceFeeCents() {
        return serviceFeeCents;
    }


}
