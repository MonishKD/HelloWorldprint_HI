package com.fortunategoods.technical.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fortunategoods.domain.restaurant.MenuItem;
import com.fortunategoods.domain.restaurant.MenuItemStatus;

// Finds, adds, changes and hides menu items in the database (#24, #145).
//
// How every method works:
//   1. write the SQL, with a ? for each value
//   2. jdbc runs it and puts the values in place of the ? (in order)
//
// Items are never deleted. hide() sets removed = TRUE, so old orders can still point to the item.
// This only saves the status. The sold-out rules are in the Domain (R-ORD-02).
@Repository
public class MenuItemRepository {

    private final JdbcTemplate jdbc;

    // Spring gives us the JdbcTemplate, it is already connected to our database.
    public MenuItemRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Returns the items on this restaurant's menu, without the hidden ones.
    public List<MenuItem> findByRestaurantId(String restaurantId) {
        int menuId = findMenuId(restaurantId);
        String sql = "SELECT * FROM menu_items WHERE menu_id = ? AND removed = FALSE ORDER BY id";
        return jdbc.query(sql, (row, rowNumber) -> toMenuItem(row), menuId);
    }

    // Adds a new item to this restaurant's menu and returns its new id.
    public String add(String restaurantId, MenuItem item) {
        int menuId = findMenuId(restaurantId);
        // RETURNING id gives us back the id the database just made for this item.
        String sql = "INSERT INTO menu_items (menu_id, name, status, price) VALUES (?, ?, ?, ?) RETURNING id";
        Integer id = jdbc.queryForObject(sql, Integer.class, menuId, item.getName(), item.getStatus().name(), item.getPrice());
        return String.valueOf(id);
    }

    // Saves the item's new name, status and price.
    public void update(MenuItem item) {
        String sql = "UPDATE menu_items SET name = ?, status = ?, price = ? WHERE id = ?";
        jdbc.update(sql, item.getName(), item.getStatus().name(), item.getPrice(), Integer.parseInt(item.getId()));
    }

    // Hides the item from the menu (it stays in the database for old orders).
    public void hide(String id) {
        String sql = "UPDATE menu_items SET removed = TRUE WHERE id = ?";
        jdbc.update(sql, Integer.parseInt(id));
    }

    // Each restaurant has one menu. This returns the id of that menu.
    private int findMenuId(String restaurantId) {
        String sql = "SELECT id FROM menus WHERE restaurant_id = ?";
        return jdbc.queryForObject(sql, Integer.class, Integer.parseInt(restaurantId));
    }

    // Turns one row of the menu_items table into a MenuItem.
    private MenuItem toMenuItem(ResultSet row) throws SQLException {
        return new MenuItem(
                row.getString("id"),
                row.getString("name"),
                MenuItemStatus.valueOf(row.getString("status")),
                row.getDouble("price"));
    }
}
