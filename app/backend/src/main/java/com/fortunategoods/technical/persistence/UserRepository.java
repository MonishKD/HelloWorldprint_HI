package com.fortunategoods.technical.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
    public UserAccount findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        List<UserAccount> found = jdbc.query(sql, (row, rowNumber) -> toUserAccount(row), username);
        if (found.isEmpty()) {
            return null;
        }
        return found.get(0);
    }

    // Returns the user with this email, or null if there is none.
    public UserAccount findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        List<UserAccount> found = jdbc.query(sql, (row, rowNumber) -> toUserAccount(row), email.toLowerCase());
        if (found.isEmpty()) {
            return null;
        }
        return found.get(0);
    }

    // Saves a new user and returns its new id.
    // It also adds a row in the table of its role (customers, couriers or operators).
    // @Transactional means: if one of the two inserts fails, neither is saved.
    @Transactional
    public String add(UserAccount user) {
        // RETURNING id gives us back the id the database just made for this user.
        String sql = "INSERT INTO users (username, email, phone_number, password_hash, role) VALUES (?, ?, ?, ?, ?) RETURNING id";
        Integer id = jdbc.queryForObject(sql, Integer.class,
                user.username(), user.email().toLowerCase(), user.phoneNumber(), user.passwordHash(), user.role());

        if (user.role().equals("CUSTOMER")) {
            jdbc.update("INSERT INTO customers (user_id) VALUES (?)", id);
        }
        if (user.role().equals("COURIER")) {
            jdbc.update("INSERT INTO couriers (user_id) VALUES (?)", id);
        }
        if (user.role().equals("OPERATOR")) {
            jdbc.update("INSERT INTO operators (user_id) VALUES (?)", id);
        }
        // RESTAURANT users are linked to their restaurant later (#144).

        return String.valueOf(id);
    }

    // Turns one row of the users table into a UserAccount.
    private UserAccount toUserAccount(ResultSet row) throws SQLException {
        return new UserAccount(
                row.getString("id"),
                row.getString("username"),
                row.getString("email"),
                row.getString("phone_number"),
                row.getString("password_hash"),
                row.getString("role"));
    }
}
