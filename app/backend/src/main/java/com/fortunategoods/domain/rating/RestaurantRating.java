package com.fortunategoods.domain.rating;

// A restaurant's average (one decimal) and how many ratings it comes from.
public record RestaurantRating(double average, int count) {}
