package com.fortunategoods.application.rvw;

// The body of POST /api/orders/{orderId}/rating. Integer (not int) so a missing field arrives as null and is refused.
public record RateOrderReqJSON(Integer restaurantStars, Integer courierStars) {}
