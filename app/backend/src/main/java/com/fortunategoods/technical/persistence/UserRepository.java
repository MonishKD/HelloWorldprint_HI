package com.fortunategoods.technical.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fortunategoods.domain.user.User;
import com.fortunategoods.domain.user.UserRole;

// Saves and finds user accounts in the database (#24).
//
// How every method works:
//   1. write the SQL, with a ? for each value
//   2. jdbc runs it and puts the values in place of the ? (in order)
//
// Emails are always saved and searched in lowercase, so Bob@mail.com and bob@mail.com are the same email.
// If the username or email is already taken, the database refuses it and Spring throws a DuplicateKeyException.
@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;

    // Spring gives us the JdbcTemplate, it is already connected to our database.
    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Returns the user with this username, or null if there is none.
    public User findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        List<User> found = jdbc.query(sql, (row, rowNumber) -> toUser(row), username);
        if (found.isEmpty()) {
            return null;
        }
        return found.get(0);
    }

    // Returns the user with this email, or null if there is none.
    public User findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        List<User> found = jdbc.query(sql, (row, rowNumber) -> toUser(row), email.toLowerCase());
        if (found.isEmpty()) {
            return null;
        }
        return found.get(0);
    }

    // Saves a new user and returns its new id.
    // It also adds a row in the table of its role (customers, couriers or operators).
    // @Transactional means: if one of the two inserts fails, neither is saved.
    @Transactional
    public String add(User user) {
        // RETURNING id gives us back the id the database just made for this user.
        String sql = "INSERT INTO users (username, email, phone_number, password_hash, role) VALUES (?, ?, ?, ?, ?) RETURNING id";
        Integer id = jdbc.queryForObject(sql, Integer.class,
                user.getUsername(), user.getEmail().toLowerCase(), user.getPhoneNumber(), user.getPasswordHash(),
                user.getRole().name());

        if (user.getRole() == UserRole.CUSTOMER) {
            jdbc.update("INSERT INTO customers (user_id) VALUES (?)", id);
        }
        if (user.getRole() == UserRole.COURIER) {
            jdbc.update("INSERT INTO couriers (user_id) VALUES (?)", id);
        }
        if (user.getRole() == UserRole.OPERATOR) {
            jdbc.update("INSERT INTO operators (user_id) VALUES (?)", id);
        }
        // RESTAURANT users are linked to their restaurant with addRestaurantPersonnel() below.

        return String.valueOf(id);
    }

    // Links a RESTAURANT user to the one restaurant they work for. isOwner = true for the owner (#144).
    public void addRestaurantPersonnel(String userId, String restaurantId, boolean isOwner) {
        String sql = "INSERT INTO restaurant_personnel (user_id, restaurant_id, is_owner) VALUES (?, ?, ?)";
        jdbc.update(sql, Integer.parseInt(userId), Integer.parseInt(restaurantId), isOwner);
    }

    // Returns the id of the restaurant this RESTAURANT user works for, or null if they have none.
    // This is how a restaurant account only ever sees its own restaurant (#144, #105).
    public String findRestaurantIdByUserId(String userId) {
        String sql = "SELECT restaurant_id FROM restaurant_personnel WHERE user_id = ?";
        List<String> found = jdbc.queryForList(sql, String.class, Integer.parseInt(userId));
        if (found.isEmpty()) {
            return null;
        }
        return found.get(0);
    }

    // Turns one row of the users table into a User.
    private User toUser(ResultSet row) throws SQLException {
        return new User(
                row.getString("id"),
                row.getString("username"),
                row.getString("email"),
                row.getString("phone_number"),
                row.getString("password_hash"),
                UserRole.valueOf(row.getString("role")));
    }
}
