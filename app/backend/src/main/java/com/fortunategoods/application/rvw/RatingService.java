package com.fortunategoods.application.rvw;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.order.validation.OrderVerify;
import com.fortunategoods.domain.rating.Rating;
import com.fortunategoods.domain.rating.RatingScale;
import com.fortunategoods.technical.persistence.OrderQueryRepository;
import com.fortunategoods.technical.persistence.RatingRepository;

// Business rules for rating an order (R-RVW-01). RateOrderCommand calls this.
// @Service: Spring makes one of this class and gives it to the classes that ask for it in their constructor.
@Service
public class RatingService {

    private final OrderQueryRepository orderRepo; // read-only order lookups (R1)
    private final RatingRepository ratingRepo;
    private final RatingScale scale;

    public RatingService(OrderQueryRepository orderRepo, RatingRepository ratingRepo, RatingScale scale) {
        this.orderRepo = orderRepo;
        this.ratingRepo = ratingRepo;
        this.scale = scale;
    }

    public RatingScale scale() {
        return scale;
    }

    // customerId comes from the session, never from the request.
    public RatingResponse rateOrder(String customerId, String orderId, Integer restaurantStars, Integer courierStars) {
        OrderVerify.requireId(orderId, "Order ID");
        Order order = orderRepo.findById(orderId);
        if (order == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "Order not found");
        }
        if (!customerId.equals(order.getCustomerId())) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN, "You can only rate your own orders");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Only delivered orders can be rated");
        }
        if (ratingRepo.findByOrder(orderId) != null) {
            throw alreadyRated();
        }

        // The courier rated is the one saved on the order (it stays set after delivery).
        Rating rating = Rating.create(scale, orderId, customerId, order.getRestaurantId(), order.getCourierId(),
                restaurantStars, courierStars);
        try {
            ratingRepo.save(rating);
        } catch (DuplicateKeyException doubleClick) {
            throw alreadyRated(); // two requests at once: the UNIQUE order_id let only one through
        }
        return new RatingResponse(orderId, rating.restaurantStars(), rating.courierStars());
    }

    private DomainRuleViolation alreadyRated() {
        return new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "This order was already rated");
    }
}
