package com.fortunategoods.application.dlv;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.fortunategoods.domain.courier.Assignment;
import com.fortunategoods.domain.courier.CourierStatus;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.technical.persistence.AssignmentRepository;
import com.fortunategoods.technical.persistence.CourierRepository;


@SpringBootTest
@Transactional

public class CourierOfferServiceTest {
    @Autowired CourierOfferService service;
    @Autowired CourierRepository couriers;
    @Autowired AssignmentRepository assignments;
    @Autowired JdbcTemplate jdbc;

    private final String courierId = "900001";
    private final String orderId = "900001";
    private final String customerId = "900002";
    private final String restaurantId = "900001";

    @BeforeEach
    void setUp() {
        // customer's user, then the customer
        jdbc.update("INSERT INTO users (id, username, email, password_hash, role) VALUES (?, ?, ?, ?, ?)",
            Integer.parseInt(customerId), "testcustomer", "testcustomer@test.com", "x", "CUSTOMER");
        jdbc.update("INSERT INTO customers (user_id, name) VALUES (?, ?)",
            Integer.parseInt(customerId), "Test Customer");

        // restaurant, with coordinates so the dispatch retry can pick the nearest courier
        jdbc.update("INSERT INTO restaurants (id, name, status, address, latitude, longitude) VALUES (?, ?, ?, ?, ?, ?)",
            Integer.parseInt(restaurantId), "Test Restaurant", "OPEN", "1 Test St", 45.5, 34.5);

        // the order, READY so assignCourierIfUnchanged can match it
        jdbc.update("INSERT INTO orders (id, customer_id, restaurant_id, status) VALUES (?, ?, ?, ?)",
            Integer.parseInt(orderId), Integer.parseInt(customerId), Integer.parseInt(restaurantId), "READY");

        // one line on the order: the dispatch queue can't load an order without one
        jdbc.update("INSERT INTO menus (id, restaurant_id) VALUES (900001, ?)", Integer.parseInt(restaurantId));
        jdbc.update("INSERT INTO menu_items (id, menu_id, name, price, status) VALUES (900001, 900001, 'Item', 5.00, 'AVAILABLE')");
        jdbc.update("INSERT INTO order_lines (order_id, menu_item_id, quantity, name, price) VALUES (?, 900001, 1, 'Item', 5.00)",
            Integer.parseInt(orderId));

        // the READY event, which the dispatch queue joins on
        jdbc.update("INSERT INTO order_events (order_id, new_status) VALUES (?, ?)",
            Integer.parseInt(orderId), "READY");
    }

    private Assignment insertOffer() {
    insertCourier(courierId, "IDLE", 45.5, 34.5);
    return assignments.add(new Assignment(null, orderId, courierId, Assignment.Status.OFFERED, false, false));
    }

    private void insertCourier(String id, String status, Double latitude, Double longitude) {
    jdbc.update("INSERT INTO users (id, username, email, password_hash, role) VALUES (?, ?, ?, ?, ?)",
        Integer.parseInt(id), "courier" + id, "courier" + id + "@test.com", "x", "COURIER");
    jdbc.update("INSERT INTO couriers (user_id, name, latitude, longitude, status) VALUES (?, ?, ?, ?, ?)",
        Integer.parseInt(id), "Courier " + id, latitude, longitude, status);
    }

    @Test 
    void acceptSuccessTest() {
        Assignment offer = insertOffer();   // IDLE courier + OFFERED assignment
        service.accept(courierId);

        // courier is now ASSIGNED
        assertEquals(CourierStatus.ASSIGNED, couriers.findById(courierId).getStatus());

        // assignment is now ACCEPTED
        String status = jdbc.queryForObject(
            "SELECT status FROM assignments WHERE id = ?", String.class, Integer.parseInt(offer.getId()));
        assertEquals("ACCEPTED", status);

        // the order now points at the courier
        Integer orderCourier = jdbc.queryForObject(
            "SELECT courier_id FROM orders WHERE id = ?", Integer.class, Integer.parseInt(orderId));
        assertEquals(Integer.parseInt(courierId), orderCourier);
    }

    @Test 
    void acceptWithNoOfferTest()
    {
        insertCourier(courierId, "IDLE", 45.5, 34.5);

        DomainRuleViolation exception = assertThrows(DomainRuleViolation.class, () -> service.accept(courierId));
        assertEquals(DomainRuleViolation.Reason.CONFLICT, exception.getReason());
        assertEquals("You have no pending offer", exception.getMessage());
    }

    @Test 
    void acceptTwiceTest()
    {
        Assignment offer = insertOffer();
        service.accept(courierId);
        DomainRuleViolation exception = assertThrows(DomainRuleViolation.class, () -> service.accept(courierId));
        assertEquals(DomainRuleViolation.Reason.CONFLICT, exception.getReason());
        assertEquals("You have no pending offer", exception.getMessage());
    }

    @Test 
    void acceptWhenNotIdleTest()
    {
        insertCourier(courierId, "ASSIGNED", 45.5, 34.5);
        Assignment offer = assignments.add(new Assignment(null, orderId, courierId, Assignment.Status.OFFERED, false, false));
        DomainRuleViolation exception = assertThrows(DomainRuleViolation.class, () -> service.accept(courierId));
        assertEquals(DomainRuleViolation.Reason.CONFLICT, exception.getReason());
        String status = jdbc.queryForObject(
            "SELECT status FROM assignments WHERE id = ?", String.class, Integer.parseInt(offer.getId()));
        assertEquals("OFFERED", status);
        assertEquals(CourierStatus.ASSIGNED, couriers.findById(courierId).getStatus());
    }

    @Test 
    void acceptCancelledOrderTest()
    {
        insertOffer();
        jdbc.update("UPDATE orders SET status = 'CANCELLED' WHERE id = ?", Integer.parseInt(orderId));
        DomainRuleViolation exception = assertThrows(DomainRuleViolation.class, () -> service.accept(courierId));
        assertEquals(DomainRuleViolation.Reason.CONFLICT, exception.getReason());
        assertEquals("Order is no longer available!", exception.getMessage());
    }

    @Test 
    void declineSuccessTest()
    {
        Assignment offer = insertOffer();
        service.decline(courierId);

        String status = jdbc.queryForObject(
            "SELECT status FROM assignments WHERE id = ?", String.class, Integer.parseInt(offer.getId()));
        assertEquals("DECLINED", status);

        assertEquals(CourierStatus.IDLE, couriers.findById(courierId).getStatus());
    }

    @Test 
    void declineWithNoOfferTest()
    {
        insertCourier(courierId, "IDLE", 45.5, 34.5);
        DomainRuleViolation exception = assertThrows(DomainRuleViolation.class, () -> service.decline(courierId));
        assertEquals(DomainRuleViolation.Reason.CONFLICT, exception.getReason());
        assertEquals("You have no pending offer", exception.getMessage());
    }

    @Test 
    void declineReoffersOrderTest()
    {
        Assignment offer = insertOffer();
        service.decline(courierId);

        String oldStatus = jdbc.queryForObject(
            "SELECT status FROM assignments WHERE id = ?", 
        String.class, Integer.parseInt(offer.getId()));
        assertEquals("DECLINED", oldStatus);

        Integer reoffered = jdbc.queryForObject(
        "SELECT COUNT(*) FROM assignments WHERE order_id = ? AND status = 'OFFERED' AND id <> ?",
        Integer.class, Integer.parseInt(orderId), Integer.parseInt(offer.getId()));
        assertEquals(1, reoffered);

    }

    @Test
    void otherCourierCannotAnswerTest() {
        Assignment offer = insertOffer();            // courier A (900001): IDLE with an OFFERED assignment
        String otherCourierId = "900003";
        insertCourier(otherCourierId, "IDLE", 22.3, 33.2);   // courier B: IDLE, no offer

        // B tries to accept A's offer
        DomainRuleViolation acceptEx = assertThrows(DomainRuleViolation.class, () -> service.accept(otherCourierId));
        assertEquals(DomainRuleViolation.Reason.CONFLICT, acceptEx.getReason());
        assertEquals("You have no pending offer", acceptEx.getMessage());

        // B tries to decline A's offer
        DomainRuleViolation declineEx = assertThrows(DomainRuleViolation.class, () -> service.decline(otherCourierId));
        assertEquals(DomainRuleViolation.Reason.CONFLICT, declineEx.getReason());
        assertEquals("You have no pending offer", declineEx.getMessage());

        // A's offer is untouched
        String status = jdbc.queryForObject(
            "SELECT status FROM assignments WHERE id = ?", String.class, Integer.parseInt(offer.getId()));
        assertEquals("OFFERED", status);
        assertEquals(CourierStatus.IDLE, couriers.findById(courierId).getStatus());
    }

}
