package com.fortunategoods.technical.persistence;

import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.dao.DuplicateKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;

import com.fortunategoods.domain.courier.*;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AssignmentRepository.class)
public class AssignmentRepositoryTest {
    @Autowired AssignmentRepository repo;
    @Autowired JdbcTemplate jdbc;

    private final String orderId = "900001";
    private final String courierId = "900001";
    private final int customerId = 900002;
    private final int restaurantId = 900001;

    @BeforeEach
    void setUp() {
    // courier's user, then the courier
    jdbc.update("INSERT INTO users (id, username, email, password_hash, role) VALUES (?, ?, ?, ?, ?)",
        Integer.parseInt(courierId), "testcourier", "testcourier@test.com", "x", "COURIER");
    jdbc.update("INSERT INTO couriers (user_id, name, latitude, longitude, status) VALUES (?, ?, ?, ?, ?)",
        Integer.parseInt(courierId), "Darcy", 30.0, 60.0, "IDLE");

    // customer's user, then the customer
    jdbc.update("INSERT INTO users (id, username, email, password_hash, role) VALUES (?, ?, ?, ?, ?)",
        customerId, "testcustomer", "testcustomer@test.com", "x", "CUSTOMER");
    jdbc.update("INSERT INTO customers (user_id, name) VALUES (?, ?)", customerId, "Test Customer");

    // restaurant
    jdbc.update("INSERT INTO restaurants (id, name, status, address) VALUES (?, ?, ?, ?)",
        restaurantId, "Test Restaurant", "OPEN", "1 Test St");

    // the order, which points at the customer and restaurant above
    jdbc.update("INSERT INTO orders (id, customer_id, restaurant_id, status) VALUES (?, ?, ?, ?)",
        Integer.parseInt(orderId), customerId, restaurantId, "PLACED");
    }

    @Test
    void testFindActiveByOrder() {
        repo.add(new Assignment(null, orderId, courierId, Assignment.Status.OFFERED, false, false));
        Assignment found = repo.findActiveByOrder(orderId);
        assertNotNull(found);
        assertEquals(courierId, found.getCourierId());
        assertEquals(Assignment.Status.OFFERED, found.getStatus());
    }

    @Test
    void testFindActiveByOrderNoneFound() {
        assertNull(repo.findActiveByOrder(orderId));
    }

    @Test
    void testDeclinedAssignmentIsNotActive() {
        Assignment saved = repo.add(new Assignment(null, orderId, courierId, Assignment.Status.OFFERED, false, false));
        repo.setStatus(Assignment.Status.DECLINED, Assignment.Status.OFFERED, saved.getId());
        assertNull(repo.findActiveByCourier(courierId));
    }

    @Test
    void testSecondActiveAssignmentIsRejected() {
        repo.add(new Assignment(null, orderId, courierId, Assignment.Status.OFFERED, false, false));
        assertThrows(DuplicateKeyException.class,
            () -> repo.add(new Assignment(null, orderId, courierId, Assignment.Status.OFFERED, false, false)));
    }

    @Test
    void testFindActiveByCourierNoneFound() {
        assertNull(repo.findActiveByCourier(courierId));
    }

    @Test
    void testAddAssignment() {
        //we add a new assignment
       Assignment saved = repo.add(new Assignment(null, orderId, courierId, Assignment.Status.OFFERED, false, false));
       //now, we test to see if all the attributes of saved match the given assignment parameters!
       assertNotNull(saved.getId()); 
       assertEquals(orderId, saved.getOrderId()); 
       assertEquals(courierId, saved.getCourierId());
       assertEquals(Assignment.Status.OFFERED, saved.getStatus());
       assertFalse(saved.isPickedUp());
       assertFalse(saved.isDelivered());
    }

    @Test 
    void testFindActiveByCourier() {
        repo.add(new Assignment(null, orderId, courierId, Assignment.Status.OFFERED, false, false));
        Assignment found = repo.findActiveByCourier(courierId);
        assertNotNull(found);
        assertEquals(orderId, found.getOrderId());
        assertEquals(courierId, found.getCourierId());
        assertEquals(Assignment.Status.OFFERED, found.getStatus());
    }

    @Test 
    void testSetStatus() {
        Assignment saved = repo.add(new Assignment(null, orderId, courierId, Assignment.Status.OFFERED, false, false));
         boolean updated = repo.setStatus(Assignment.Status.ACCEPTED, Assignment.Status.OFFERED, saved.getId());
         assertTrue(updated); //proves that the status was changed

        //double checking the status change by looking it up in the repo
        Assignment found = repo.findActiveByOrder(orderId);
        assertEquals(Assignment.Status.ACCEPTED, found.getStatus());

    }

    @Test
    void testSetStatusWrongCurrentStatus() {
    Assignment saved = repo.add(new Assignment(null, orderId, courierId, Assignment.Status.OFFERED, false, false));
    boolean updated = repo.setStatus(Assignment.Status.DECLINED, Assignment.Status.ACCEPTED, saved.getId());
    assertFalse(updated);

    assertEquals(Assignment.Status.OFFERED, repo.findActiveByOrder(orderId).getStatus());
    }
    
}
