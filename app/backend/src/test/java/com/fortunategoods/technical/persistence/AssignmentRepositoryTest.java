package com.fortunategoods.technical.persistence;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;


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

    private final String orderId = "1";
    private final String courierId = "3454";

    @BeforeEach 
    void setUp()
    {
    jdbc.update("INSERT INTO couriers (user_id, name, latitude, longitude, status) VALUES (?, ?, ?, ?, ?)",
        Integer.parseInt(courierId), "Darcy", 30.0, 60.0, "IDLE");

    jdbc.update("INSERT INTO orders (id, status) VALUES (?, ?)", Integer.parseInt(orderId), "CREATED");
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
