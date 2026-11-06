package com.fortunategoods.technical.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fortunategoods.domain.order.DomainRuleViolation;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentIntentRetrieveParams;
import com.stripe.param.RefundCreateParams;

// The real PaymentGateway (#258), and the only class in the app that talks to Stripe (test mode).
//
// Stripe's "PaymentIntent" is one payment from start to end. We create it with manual capture, so Stripe first
// only HOLDS the money on the card (authorize), and takes it later when the order is delivered (capture).
// The card itself is typed in the browser and goes straight to Stripe (#260), our server never sees the number.
//
// @Component makes Spring create one of these at startup and give it to anyone asking for a PaymentGateway.
@Component
public class StripePaymentGateway implements PaymentGateway {

    // The platform is in Montreal, so every payment is in Canadian dollars.
    private static final String CURRENCY = "cad";

    // Stripe's status for "the money is held on the card, waiting to be captured".
    private static final String HELD_STATUS = "requires_capture";

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGateway.class);

    // Stripe's own client, built once with our secret test key. Every Stripe call goes through it.
    private final StripeClient stripe;

    // @Value fills secretKey from stripe.secret-key in application.properties (STRIPE_SECRET_KEY in .env).
    public StripePaymentGateway(@Value("${stripe.secret-key}") String secretKey) {
        this.stripe = new StripeClient(secretKey);
    }

    // Creates the payment and holds nothing yet: the browser confirms the card with the client secret afterwards.
    // Cards only, so Stripe's card field in the cart shows just the card form (#260).
    @Override
    public Payment authorize(long amountCents) {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountCents)
                .setCurrency(CURRENCY)
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                .addAllowedPaymentMethodType(PaymentIntentCreateParams.AllowedPaymentMethodType.CARD)
                .build();
        try {
            PaymentIntent intent = stripe.v1().paymentIntents().create(params);
            return new Payment(intent.getId(), intent.getClientSecret(), false, intent.getAmount(), null, null);
        } catch (StripeException e) {
            throw refused("authorized", e);
        }
    }

    // Reads the payment back. "latest_charge" is asked for too, because that is where Stripe keeps the card's
    // brand and last 4 digits. Before the card is confirmed there is no charge yet, so those stay empty.
    @Override
    public Payment find(String paymentId) {
        PaymentIntentRetrieveParams params = PaymentIntentRetrieveParams.builder()
                .addExpand("latest_charge")
                .build();
        try {
            PaymentIntent intent = stripe.v1().paymentIntents().retrieve(paymentId, params);

            String cardBrand = null;
            String cardLast4 = null;
            Charge charge = intent.getLatestChargeObject();
            if (charge != null && charge.getPaymentMethodDetails() != null
                    && charge.getPaymentMethodDetails().getCard() != null) {
                cardBrand = charge.getPaymentMethodDetails().getCard().getBrand();
                cardLast4 = charge.getPaymentMethodDetails().getCard().getLast4();
            }

            boolean held = HELD_STATUS.equals(intent.getStatus());
            return new Payment(intent.getId(), intent.getClientSecret(), held, intent.getAmount(), cardBrand, cardLast4);
        } catch (StripeException e) {
            throw refused("read", e);
        }
    }

    @Override
    public void capture(String paymentId) {
        try {
            stripe.v1().paymentIntents().capture(paymentId);
        } catch (StripeException e) {
            throw refused("captured", e);
        }
    }

    // Cancelling a PaymentIntent that is only held releases the hold, the customer is never charged.
    @Override
    public void cancel(String paymentId) {
        try {
            stripe.v1().paymentIntents().cancel(paymentId);
        } catch (StripeException e) {
            throw refused("released", e);
        }
    }

    // Refunds the full captured amount. The refund is a new object at Stripe, the original payment does not change.
    @Override
    public void refund(String paymentId) {
        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(paymentId)
                .build();
        try {
            stripe.v1().refunds().create(params);
        } catch (StripeException e) {
            throw refused("refunded", e);
        }
    }

    // Turns any Stripe error into a refusal the console can show (CommandInvoker logs it as REFUSED, not a 500).
    // Stripe's full error and request id stay in the backend logs, so a failure can be looked up in the Stripe dashboard.
    private DomainRuleViolation refused(String action, StripeException e) {
        log.warn("Stripe: payment could not be {} (request {}): {}", action, e.getRequestId(), e.getMessage());

        // A declined card has a message written for customers ("Your card was declined."), other errors only have Stripe's.
        String reason = e.getUserMessage();
        if (reason == null && e.getStripeError() != null) {
            reason = e.getStripeError().getMessage();
        }
        if (reason == null) {
            reason = "the payment service could not be reached";
        }
        return new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Payment could not be " + action + ": " + reason);
    }
}
