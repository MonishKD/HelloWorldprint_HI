package com.fortunategoods.technical.payment;

// A payment as PaymentGateway sees it, so the rest of the app never touches Stripe's own classes.
public class Payment {

    // The payment's id at Stripe ("pi_..."), saved on the order so we can capture, cancel or refund it later.
    private final String id;

    // Sent to the browser only, so Stripe's card field can confirm the card (#260). Never stored.
    private final String clientSecret;

    // True when the money is held on the card, waiting to be taken (Stripe status "requires_capture").
    private final boolean held;

    // How much is held, in cents like Fare, compared with the fare before the order is placed (#259).
    private final long amountCents;

    // The only card details we keep (for example "visa" and "4242"), empty until the card is confirmed.
    private final String cardBrand;
    private final String cardLast4;

    public Payment(String id, String clientSecret, boolean held, long amountCents, String cardBrand, String cardLast4) {
        this.id = id;
        this.clientSecret = clientSecret;
        this.held = held;
        this.amountCents = amountCents;
        this.cardBrand = cardBrand;
        this.cardLast4 = cardLast4;
    }

    public String getId() {
        return id;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public boolean isHeld() {
        return held;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getCardBrand() {
        return cardBrand;
    }

    public String getCardLast4() {
        return cardLast4;
    }
}
