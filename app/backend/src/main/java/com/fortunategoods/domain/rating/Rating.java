package com.fortunategoods.domain.rating;

import com.fortunategoods.domain.order.DomainRuleViolation;

// What a customer gave for one delivered order: stars for the restaurant and stars for the courier (R-RVW-01).
// create() refuses stars outside the configured range, so a Rating made through it is always valid.
public record Rating(String orderId, String customerId, String restaurantId, String courierId,
        int restaurantStars, int courierStars) {

    public static Rating create(RatingScale scale, String orderId, String customerId, String restaurantId,
            String courierId, Integer restaurantStars, Integer courierStars) {
        if (restaurantStars == null || courierStars == null
                || !scale.contains(restaurantStars) || !scale.contains(courierStars)) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID,
                    "Ratings go from " + scale.minStars() + " to " + scale.maxStars());
        }
        return new Rating(orderId, customerId, restaurantId, courierId, restaurantStars, courierStars);
    }
}
