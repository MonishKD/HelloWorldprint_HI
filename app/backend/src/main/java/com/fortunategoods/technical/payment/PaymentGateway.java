package com.fortunategoods.technical.payment;

/*
    The only door between our app and the payment provider (#258). The order and PRC code (#259) only ever
    sees this interface, never Stripe itself. StripePaymentGateway is the real one; a new way to pay would be
    another class implementing this, without changing the code that uses it.

    How much to charge is NOT decided here: that is the Fare from domain.pricing (Kaue's pricing-rule strategies).
    This only moves the money: hold it at checkout, take it on delivery, release it, or give it back.

    Amounts are in cents (long), the same as Fare and the same as Stripe, so nothing is converted.
    When the provider says no (declined card, wrong state, no internet) it throws a DomainRuleViolation with a clear
    message, so CommandInvoker shows it in the console as a refusal, never a 500.
*/
public interface PaymentGateway {

    // Checkout: holds the amount on the customer's card without taking it yet (Stripe "manual capture").
    // The returned Payment has the id to save on the order and the client secret for the browser's card field.
    Payment authorize(long amountCents);

    // Reads a payment back after the browser confirmed the card: is the money held, for how much, and which card.
    // #259 places the order only if it is held for exactly the fare.
    Payment find(String paymentId);

    // Delivered: actually takes the held money.
    void capture(String paymentId);

    // Rejected or cancelled: releases the hold, so the customer is never charged.
    void cancel(String paymentId);

    // Operator refund (#266): gives back money that was already taken.
    void refund(String paymentId);
}
