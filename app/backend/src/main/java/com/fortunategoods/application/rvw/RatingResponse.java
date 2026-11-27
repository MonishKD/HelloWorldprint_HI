package com.fortunategoods.application.rvw;

// What the customer gets back after rating an order.
public record RatingResponse(String orderId, int restaurantStars, int courierStars) {}
