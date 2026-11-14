package com.fortunategoods.application.dlv;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;


import com.fortunategoods.technical.persistence.AssignmentRepository;
import com.fortunategoods.technical.persistence.CourierRepository;

import org.springframework.transaction.annotation.Transactional;

import com.fortunategoods.domain.courier.CourierStatus;
import com.fortunategoods.domain.order.DomainRuleViolation;

import com.fortunategoods.domain.courier.Assignment;

@SpringBootTest 
@Transactional 
public class CourierDutyServiceTest {
    @Autowired CourierDutyService service;
    @Autowired CourierRepository couriers;
    @Autowired AssignmentRepository assignments;
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


        // insert restaurant
        jdbc.update("INSERT INTO restaurants (id, name, status, address) VALUES (?, ?, ?, ?)",
            Integer.parseInt(restaurantId), "Test Restaurant", "OPEN", "1 Test St");

        // insert order
        jdbc.update("INSERT INTO orders (id, customer_id, restaurant_id, status) VALUES (?, ?, ?, ?)",
            Integer.parseInt(orderId),  Integer.parseInt(customerId),  Integer.parseInt(restaurantId), "PLACED");
    }

    //every test will involve a courier in a different state so this improves code reading!
    private void insertCourier(String status, Double latitude, Double longitude) {
        jdbc.update("INSERT INTO users (id, username, email, password_hash, role) VALUES (?, ?, ?, ?, ?)",
            Integer.parseInt(courierId), "testcourier", "testcourier@test.com", "x", "COURIER");
        jdbc.update("INSERT INTO couriers (user_id, name, latitude, longitude, status) VALUES (?, ?, ?, ?, ?)",
            Integer.parseInt(courierId), "Darcy", latitude, longitude, status);
    }

    @Test 
    void goOnDutyFromOfflineTest()
    {
        insertCourier("OFFLINE", 45.5, 34.5);
        service.goOnDuty(courierId);
        assertEquals(CourierStatus.IDLE, couriers.findById(courierId).getStatus());
    }

    @Test 
    void goOnDutyFromIdleTest()
    {
        insertCourier("IDLE", 45.5, 30.5);
        DomainRuleViolation exception = assertThrows(DomainRuleViolation.class, () -> service.goOnDuty(courierId));
        assertEquals(DomainRuleViolation.Reason.CONFLICT, exception.getReason());
        assertEquals(CourierStatus.IDLE, couriers.findById(courierId).getStatus());
    }

    @Test 
    void goOnDutyFromNullTest()
    {
        //a null status should be switched to IDLE!
        insertCourier(null, 45.5, 30.5);
        service.goOnDuty(courierId);
        assertEquals(CourierStatus.IDLE, couriers.findById(courierId).getStatus());
    }

    @Test
    void goOnDutyFromNullPositionTest()
    {
        insertCourier("OFFLINE", null, null);
        DomainRuleViolation exception = assertThrows(DomainRuleViolation.class, () -> service.goOnDuty(courierId));
        assertEquals(DomainRuleViolation.Reason.CONFLICT, exception.getReason());
        assertEquals(CourierStatus.OFFLINE, couriers.findById(courierId).getStatus());
    }

    @Test 
    void goOnDutyWhileAssignedTest()
    {
        insertCourier("ASSIGNED", 34.4, 44.4);
        DomainRuleViolation exception = assertThrows(DomainRuleViolation.class, () -> service.goOnDuty(courierId));
        assertEquals(DomainRuleViolation.Reason.CONFLICT, exception.getReason());
        assertEquals(CourierStatus.ASSIGNED, couriers.findById(courierId).getStatus());
    }

    @Test 
    void goOnDutyWhileCarryingTest()
    {
        insertCourier("CARRYING", 33.3, 33.3);
        DomainRuleViolation exception = assertThrows(DomainRuleViolation.class, () -> service.goOnDuty(courierId));
        assertEquals(DomainRuleViolation.Reason.CONFLICT, exception.getReason());
        assertEquals(CourierStatus.CARRYING, couriers.findById(courierId).getStatus());
    }

    @Test 
    void goOnDutyWithUnknownCourierTest()
    {
        DomainRuleViolation exception = assertThrows(DomainRuleViolation.class, () -> service.goOnDuty("999999"));
        assertEquals(DomainRuleViolation.Reason.NOT_FOUND, exception.getReason());
    }


    //now for the goOffDuty tests

    @Test 
    void goOffDutyFromIdleTest()
    {
        insertCourier("IDLE", 40.3, 20.1);
        service.goOffDuty(courierId);
        assertEquals(CourierStatus.OFFLINE, couriers.findById(courierId).getStatus());
    }

    @Test 
    void goOffDutyWhileAlreadyOfflineTest()
    {
        insertCourier("OFFLINE", 40.3, 20.1);
        DomainRuleViolation exception = assertThrows(DomainRuleViolation.class, () -> service.goOffDuty(courierId));
        assertEquals(DomainRuleViolation.Reason.CONFLICT, exception.getReason());
        assertEquals(CourierStatus.OFFLINE, couriers.findById(courierId).getStatus());
    }

    @Test 
    void goOffDutyWithUnknownCourierTest()
    {
        DomainRuleViolation exception = assertThrows(DomainRuleViolation.class, () -> service.goOffDuty("999999"));
        assertEquals(DomainRuleViolation.Reason.NOT_FOUND, exception.getReason());
    }

    @Test 
    void goOffDutyWhileCarryingTest()
    {
        insertCourier("CARRYING", 40.3, 20.1);
        DomainRuleViolation exception = assertThrows(DomainRuleViolation.class, () -> service.goOffDuty(courierId));
        assertEquals(DomainRuleViolation.Reason.CONFLICT, exception.getReason());
        assertEquals(CourierStatus.CARRYING, couriers.findById(courierId).getStatus());
    }

    @Test 
    void goOffDutyWhileAssignedTest()
    {
        insertCourier("ASSIGNED", 40.3, 20.1);
        service.goOffDuty(courierId);
        assertEquals(CourierStatus.OFFLINE, couriers.findById(courierId).getStatus());
    }

    @Test 
    void goOffDutyWithActiveOfferTest()
    {
        insertCourier("IDLE", 40.3, 20.1);
        Assignment offer = assignments.add(new Assignment(null, orderId, courierId, Assignment.Status.OFFERED, false, false));
        service.goOffDuty(courierId);
        String status = jdbc.queryForObject("SELECT status FROM assignments WHERE id = ?", String.class, Integer.parseInt(offer.getId()));
        assertEquals("WITHDRAWN", status);
    }

    @Test 
    void goOffDutyWhileAssignedLeavesAssignmentAcceptedTest()
    {
        insertCourier("ASSIGNED", 40.3, 20.1);
        assignments.add(new Assignment(null, orderId, courierId, Assignment.Status.ACCEPTED, false, false));
        service.goOffDuty(courierId);
        assertEquals(CourierStatus.OFFLINE, couriers.findById(courierId).getStatus());
        Assignment active = assignments.findActiveByCourier(courierId);
        assertEquals(Assignment.Status.ACCEPTED, active.getStatus());
    }

    @Test 
    void goOffDutyWhileCarryingLeavesAssignmentUntouchedTest()
    {
        insertCourier("CARRYING", 40.3, 2.1);
        Assignment carried = assignments.add(new Assignment(null, orderId, courierId, Assignment.Status.ACCEPTED, true, false));
        DomainRuleViolation exception = assertThrows(DomainRuleViolation.class, () -> service.goOffDuty(courierId));
        assertEquals(DomainRuleViolation.Reason.CONFLICT, exception.getReason());
        assertEquals(CourierStatus.CARRYING, couriers.findById(courierId).getStatus());

        String status = jdbc.queryForObject(
            "SELECT status FROM assignments WHERE id = ?", String.class, Integer.parseInt(carried.getId()));
        Boolean pickedUp = jdbc.queryForObject(
            "SELECT is_picked_up FROM assignments WHERE id = ?", Boolean.class, Integer.parseInt(carried.getId()));
        assertEquals("ACCEPTED", status);
        assertTrue(pickedUp);
    }

    


}
