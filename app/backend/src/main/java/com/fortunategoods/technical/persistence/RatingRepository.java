package com.fortunategoods.technical.persistence;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fortunategoods.domain.rating.Rating;
import com.fortunategoods.domain.rating.RestaurantRating;

// Saves and reads ratings in the database (#324, R-RVW-01, R-RVW-02).
@Repository
public class RatingRepository {

    private final JdbcTemplate jdbc;

    public RatingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Throws DuplicateKeyException if this order is already rated (the UNIQUE order_id).
    public void save(Rating rating) {
        jdbc.update("INSERT INTO ratings (order_id, customer_id, restaurant_id, courier_id, restaurant_stars, courier_stars) "
                + "VALUES (?, ?, ?, ?, ?, ?)", Integer.parseInt(rating.orderId()), Integer.parseInt(rating.customerId()),
                Integer.parseInt(rating.restaurantId()), Integer.parseInt(rating.courierId()),
                rating.restaurantStars(), rating.courierStars());
    }

    // The rating of this order, or null when it is not rated yet.
    public Rating findByOrder(String orderId) {
        List<Rating> found = jdbc.query("SELECT * FROM ratings WHERE order_id = ?",
                (row, rowNumber) -> new Rating(row.getString("order_id"), row.getString("customer_id"),
                        row.getString("restaurant_id"), row.getString("courier_id"),
                        row.getInt("restaurant_stars"), row.getInt("courier_stars")),
                Integer.parseInt(orderId));
        return found.isEmpty() ? null : found.get(0);
    }

    // Average (one decimal) and count of the restaurant stars, by restaurant id. A restaurant with no rating is not in the map.
    public Map<String, RestaurantRating> findRestaurantRatings() {
        Map<String, RestaurantRating> ratings = new HashMap<>();
        jdbc.query("SELECT restaurant_id, ROUND(AVG(restaurant_stars), 1) AS average, COUNT(*) AS total "
                + "FROM ratings GROUP BY restaurant_id",
                row -> { ratings.put(row.getString("restaurant_id"),
                        new RestaurantRating(row.getDouble("average"), row.getInt("total"))); });
        return ratings;
    }
}
