package com.fortunategoods.technical.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fortunategoods.domain.restaurant.MenuItem;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;

// Finds restaurants and changes their status in the database (#24, #149).
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
