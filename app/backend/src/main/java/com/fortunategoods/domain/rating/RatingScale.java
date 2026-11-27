package com.fortunategoods.domain.rating;

// The lowest and highest star a customer can give. It comes from the "ratings" block of seed-config.json (Phase IV 5.1).
public record RatingScale(int minStars, int maxStars) {

    public boolean contains(int stars) {
        return stars >= minStars && stars <= maxStars;
    }
}
