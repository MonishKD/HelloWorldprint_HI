package com.fortunategoods.application.prc.checkout;

import com.fortunategoods.domain.pricing.Fare;

/*
    Class used to send the checkout to the frontend (#259, #260): the fare's parts for the cart (in cents),
    and what the card field needs to confirm the card with Stripe.
    paymentId is sent back when placing the order, clientSecret is only used by Stripe's card field.
*/
public class CheckoutResponse {
    private final String paymentId;
    private final String clientSecret;
    private final long subtotalCents;
    private final long deliveryFeeCents;
    private final long serviceFeeCents;
    private final long surchargeCents;
    private final long totalCents;

    public CheckoutResponse(String paymentId, String clientSecret, Fare fare) {
        this.paymentId = paymentId;
        this.clientSecret = clientSecret;
        this.subtotalCents = fare.getSubtotalCents();
        this.deliveryFeeCents = fare.getDeliveryFeeCents();
        this.serviceFeeCents = fare.getServiceFeeCents();
        this.surchargeCents = fare.getSurchargeCents();
        this.totalCents = fare.getTotalCents();
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public long getSubtotalCents() {
        return subtotalCents;
    }

    public long getDeliveryFeeCents() {
        return deliveryFeeCents;
    }

    public long getServiceFeeCents() {
        return serviceFeeCents;
    }

    public long getSurchargeCents() {
        return surchargeCents;
    }

    public long getTotalCents() {
        return totalCents;
    }
}
