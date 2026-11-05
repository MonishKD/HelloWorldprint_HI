package com.fortunategoods.application.prc;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.fortunategoods.application.ord.orders.OrderService;
import com.fortunategoods.application.ord.orders.OrderServiceResult;
import com.fortunategoods.application.prc.checkout.CheckoutResponse;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrderFinished;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.order.validation.OrderVerify;
import com.fortunategoods.domain.order.validation.RequestedOrderLineItem;
import com.fortunategoods.domain.pricing.Fare;
import com.fortunategoods.domain.pricing.FareCalculator;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.technical.payment.Payment;
import com.fortunategoods.technical.payment.PaymentGateway;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;

import jakarta.transaction.Transactional;

/*
    Connects card payment to the order flow (#259). The customer pays when placing, the money is only taken on delivery:
      1. startCheckout: price the cart with the pricing rules (Fare) and hold that amount on the card
      2. the browser confirms the card with Stripe (#260)
      3. placePaidOrder: place the order only if the money is held for exactly that fare, and remember the payment
      4. onOrderFinished: delivered -> take the money, rejected or cancelled -> release it
      5. refundOrder: the operator's refund (#266) gives the money back

    How much to charge comes from Kaue's FareCalculator (pricing-rule strategies), never from the browser.
    Moving the money goes through PaymentGateway, so nothing here knows it is Stripe.
*/
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentGateway paymentGateway;
    private final FareCalculator fareCalculator;
    private final OrderService orderService;
    private final OrderRepository orderRepo;
    private final RestaurantRepository restaurantRepo;

    public PaymentService(PaymentGateway paymentGateway, FareCalculator fareCalculator, OrderService orderService,
            OrderRepository orderRepo, RestaurantRepository restaurantRepo) {
        this.paymentGateway = paymentGateway;
        this.fareCalculator = fareCalculator;
        this.orderService = orderService;
        this.orderRepo = orderRepo;
        this.restaurantRepo = restaurantRepo;
    }

    // Step 1: prices the cart and holds that amount on the card. Returns the fare's parts for the cart,
    // and the payment's id and client secret for the browser's card field.
    public CheckoutResponse startCheckout(String restaurantId, List<RequestedOrderLineItem> requestedItems) {
        Fare fare = fareForCart(restaurantId, requestedItems);
        Payment payment = paymentGateway.authorize(fare.getTotalCents());
        return new CheckoutResponse(payment.getId(), payment.getClientSecret(), fare);
    }

    // Step 3: places the order only if the card payment is held for exactly the fare of these lines,
    // then saves the payment id and card brand/last 4 on the order, all in one transaction.
    // If anything refuses the order (card not held, wrong amount, closed restaurant, sold-out item...),
    // the hold is released so the customer is never charged for an order that does not exist.
    @Transactional
    public OrderServiceResult placePaidOrder(String customerId, String restaurantId, List<RequestedOrderLineItem> requestedItems,
            String instructions, String deliveryAddress, String paymentId) {
        if (paymentId == null || paymentId.isBlank()) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Pay by card before placing the order");
        }
        // Checked before the try on purpose: this payment belongs to an order that exists, so its hold must not be released.
        if (orderRepo.isPaymentUsed(paymentId)) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "This card payment was already used for another order");
        }

        try {
            Payment payment = paymentGateway.find(paymentId);
            if (!payment.isHeld()) {
                throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "The card payment was not authorized, so the order was not placed");
            }

            Fare fare = fareForCart(restaurantId, requestedItems);
            if (payment.getAmountCents() != fare.getTotalCents()) {
                throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "The card payment does not match the order's price, so the order was not placed");
            }

            OrderServiceResult result = orderService.placeOrder(customerId, restaurantId, requestedItems, instructions, deliveryAddress);
            orderRepo.savePayment(result.getOrderId(), payment.getId(), payment.getCardBrand(), payment.getCardLast4());
            return result;
        } catch (RuntimeException e) {
            releaseAfterRefusedOrder(paymentId);
            throw e;
        }
    }

    // Step 4: OrderService publishes OrderFinished when an order is delivered, rejected or cancelled.
    // Plain @EventListener: it runs inside the same transaction as the status change, so if Stripe refuses,
    // the status change is rolled back too. Orders placed before card payment existed have no payment, nothing to do.
    @EventListener
    public void onOrderFinished(OrderFinished event) {
        String paymentId = orderRepo.findPaymentId(event.getOrderId());
        if (paymentId == null) {
            return;
        }

        OrderStatus status = event.getFinalStatus();
        if (status == OrderStatus.DELIVERED) {
            paymentGateway.capture(paymentId);
        } else if (status == OrderStatus.REJECTED || status == OrderStatus.CANCELLED) {
            paymentGateway.cancel(paymentId);
        }
    }

    // Step 5, for the refund command (#266): gives back the money of a delivered order.
    public void refundOrder(String orderId) {
        String paymentId = orderRepo.findPaymentId(orderId);
        if (paymentId == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "This order was not paid by card, there is nothing to refund");
        }
        paymentGateway.refund(paymentId);
    }

    // The fare of a cart, priced the same way as at placement: the lines take the menu's current prices (OrderVerify),
    // then the pricing rules add the fees. So the amount held at checkout equals the amount checked at placement.
    private Fare fareForCart(String restaurantId, List<RequestedOrderLineItem> requestedItems) {
        OrderVerify.requireId(restaurantId, "Restaurant ID");
        Restaurant restaurant = restaurantRepo.findById(restaurantId);
        List<OrderLine> lines = OrderVerify.prepareLines(restaurant, requestedItems);
        return fareCalculator.calculate(lines);
    }

    // Releasing must not hide the reason the order was refused, so a failed release is only written in the backend logs.
    // An unreleased hold expires on its own at Stripe after about 7 days.
    private void releaseAfterRefusedOrder(String paymentId) {
        try {
            paymentGateway.cancel(paymentId);
        } catch (RuntimeException releaseError) {
            log.warn("Could not release card payment {} after the order was refused: {}", paymentId, releaseError.getMessage());
        }
    }
}
