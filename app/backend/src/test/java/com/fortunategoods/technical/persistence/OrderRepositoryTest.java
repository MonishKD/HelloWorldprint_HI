package com.fortunategoods.technical.persistence;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional

public class OrderRepositoryTest {

    @Autowired OrderRepository repo;
    @Autowired JdbcTemplate jdbc;

    private final String courierId = "900001";
    private final String orderId = "900001";
    private final String customerId = "900002";
    private final String restaurantId = "900001";

    @BeforeEach 
    void setUp()
    {
        jdbc.update("INSERT INTO users (id, username, email, password_hash, role) VALUES (?, ?, ?, ?, ?)",
        Integer.parseInt(customerId), "testcustomer", "testcustomer@test.com", "x", "CUSTOMER");
        jdbc.update("INSERT INTO customers (user_id, name) VALUES (?, ?)",
            Integer.parseInt(customerId), "Test Customer");

        // restaurant
        jdbc.update("INSERT INTO restaurants (id, name, status, address) VALUES (?, ?, ?, ?)",
            Integer.parseInt(restaurantId), "Test Restaurant", "OPEN", "1 Test St");

        // the order, READY so assignCourierIfUnchanged can match it
        jdbc.update("INSERT INTO orders (id, customer_id, restaurant_id, status) VALUES (?, ?, ?, ?)",
            Integer.parseInt(orderId), Integer.parseInt(customerId), Integer.parseInt(restaurantId), "READY");

        // the courier's user, then the courier (orders.courier_id is a foreign key to couriers)
        jdbc.update("INSERT INTO users (id, username, email, password_hash, role) VALUES (?, ?, ?, ?, ?)",
            Integer.parseInt(courierId), "testcourier", "testcourier@test.com", "x", "COURIER");
        jdbc.update("INSERT INTO couriers (user_id, name, latitude, longitude, status) VALUES (?, ?, ?, ?, ?)",
            Integer.parseInt(courierId), "Courier", 45.5, 34.5, "IDLE");
        
    }

    @Test 
    void assignsCourierToReadyOrder()
    {
        assertTrue(repo.assignCourierIfUnchanged(orderId, courierId));
        Integer assigned = jdbc.queryForObject(
            "SELECT courier_id FROM orders WHERE id = ?", Integer.class, Integer.parseInt(orderId));
        assertEquals(Integer.parseInt(courierId), assigned);
    }

    @Test
    void secondAssignReturnsFalse() {
        assertTrue(repo.assignCourierIfUnchanged(orderId, courierId));
        assertFalse(repo.assignCourierIfUnchanged(orderId, courierId));
    }

    @Test
    void doesNotAssignNonReadyOrder() {
        jdbc.update("UPDATE orders SET status = 'PLACED' WHERE id = ?", Integer.parseInt(orderId));
        assertFalse(repo.assignCourierIfUnchanged(orderId, courierId));
        Integer assigned = jdbc.queryForObject(
            "SELECT courier_id FROM orders WHERE id = ?", Integer.class, Integer.parseInt(orderId));
        assertNull(assigned);
    }
}
