package com.fortunategoods.technical.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fortunategoods.domain.restaurant.MenuItem;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;

// Adds and finds restaurants and changes their status in the database (#24, #149).
//
// How every method works:
//   1. write the SQL, with a ? for each value
//   2. jdbc runs it and puts the values in place of the ? (in order)
//
// This only saves the status. The rules for open / paused / closed are in the Domain (R-ORD-02).
@Repository
public class RestaurantRepository {

    private final JdbcTemplate jdbc;
    private final MenuItemRepository menuItemRepository;

    // Spring gives us both of these. We use menuItemRepository to get each restaurant's menu.
    public RestaurantRepository(JdbcTemplate jdbc, MenuItemRepository menuItemRepository) {
        this.jdbc = jdbc;
        this.menuItemRepository = menuItemRepository;
    }

    // Returns every restaurant, each with its menu.
    public List<Restaurant> findAll() {
        String sql = "SELECT * FROM restaurants ORDER BY id";
        return jdbc.query(sql, (row, rowNumber) -> toRestaurant(row));
    }

    // Returns the restaurant with this id, or null if there is none.
    public Restaurant findById(String id) {
        String sql = "SELECT * FROM restaurants WHERE id = ?";
        List<Restaurant> found = jdbc.query(sql, (row, rowNumber) -> toRestaurant(row), Integer.parseInt(id));
        if (found.isEmpty()) {
            return null;
        }
        return found.get(0);
    }

    // Saves a new restaurant with an empty menu, and returns its new id. Used by seeding (#26) and restaurant sign-up (#143).
    // cuisine and address are passed separately because the Restaurant class does not have them yet.
    // address is required, cuisine can be null.
    // @Transactional means: if one of the two inserts fails, neither is saved.
    @Transactional
    public String add(Restaurant restaurant, String cuisine, String address) {
        // RETURNING id gives us back the id the database just made for this restaurant.
        String sql = "INSERT INTO restaurants (name, status, latitude, longitude, cuisine, address) VALUES (?, ?, ?, ?, ?, ?) RETURNING id";
        Integer id = jdbc.queryForObject(sql, Integer.class, restaurant.getName(), restaurant.getStatus().name(),
                restaurant.getLatitude(), restaurant.getLongitude(), cuisine, address);

        jdbc.update("INSERT INTO menus (restaurant_id) VALUES (?)", id);
        return String.valueOf(id);
    }

    // Changes the restaurant's status to OPEN, PAUSED or CLOSED.
    public void updateStatus(String id, RestaurantStatus status) {
        String sql = "UPDATE restaurants SET status = ? WHERE id = ?";
        jdbc.update(sql, status.name(), Integer.parseInt(id));
    }

    // Turns one row of the restaurants table into a Restaurant, with its menu.
    private Restaurant toRestaurant(ResultSet row) throws SQLException {
        String id = row.getString("id");
        List<MenuItem> menu = menuItemRepository.findByRestaurantId(id);
        return new Restaurant(
                id,
                row.getString("name"),
                RestaurantStatus.valueOf(row.getString("status")),
                row.getDouble("latitude"),
                row.getDouble("longitude"),
                menu);
    }
}
