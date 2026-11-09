package com.fortunategoods.domain.courier;

import org.junit.jupiter.api.Test;

import com.fortunategoods.domain.order.DomainRuleViolation;

import static org.junit.jupiter.api.Assertions.*;


public class CourierTest {


    @Test 
    public void testGoOnDutyWithNullLocationTotal() {
        Courier courier = new Courier("1234", "Darcy", null, null, CourierStatus.OFFLINE);
        DomainRuleViolation e = assertThrows(DomainRuleViolation.class, courier::goOnDuty);
        assertEquals("Courier should set their location before going on duty", e.getMessage());
        assertEquals(CourierStatus.OFFLINE, courier.getStatus());

    }

    @Test
    public void testGoOnDutyWithNullLatitude() {
        Courier courier = new Courier("1234", "Darcy", null, 50.5, CourierStatus.OFFLINE);
        DomainRuleViolation e = assertThrows(DomainRuleViolation.class, courier::goOnDuty);
        assertEquals("Courier should set their location before going on duty", e.getMessage());
        assertEquals(CourierStatus.OFFLINE, courier.getStatus());

    }

    @Test
    public void testGoOnDutyWithNullLongitude() {
        Courier courier = new Courier("1234", "Darcy", 60.6, null, CourierStatus.OFFLINE);
        DomainRuleViolation e = assertThrows(DomainRuleViolation.class, courier::goOnDuty);
        assertEquals("Courier should set their location before going on duty", e.getMessage());
        assertEquals(CourierStatus.OFFLINE, courier.getStatus());

    }
    
    @Test 
    public void goOnDutyWhileCarrying() {
        Courier courier = new Courier("1234", "Darcy", 60.6, 50.5, CourierStatus.CARRYING);
        DomainRuleViolation e = assertThrows(DomainRuleViolation.class, courier::goOnDuty);
        assertEquals("Courier is already on duty!", e.getMessage());
        assertEquals(CourierStatus.CARRYING, courier.getStatus());
    }

    @Test 
    public void goOnDutyWhileAssigned() {
        Courier courier = new Courier("1234", "Darcy", 60.6, 50.5, CourierStatus.ASSIGNED);
        DomainRuleViolation e = assertThrows(DomainRuleViolation.class, courier::goOnDuty);
        assertEquals("Courier is already on duty!", e.getMessage());
        assertEquals(CourierStatus.ASSIGNED, courier.getStatus());
    }

    @Test 
    public void goOnDutyWhileIdle() {
        Courier courier = new Courier("1234", "Darcy", 60.6, 50.5, CourierStatus.IDLE);
        DomainRuleViolation e = assertThrows(DomainRuleViolation.class, courier::goOnDuty);
        assertEquals("Courier is already on duty!", e.getMessage());
        assertEquals(CourierStatus.IDLE, courier.getStatus());
    }

    @Test 
    public void goOffDutyWhileCarrying() {
        Courier courier = new Courier("1234", "Darcy", 60.6, 50.5, CourierStatus.CARRYING);
        DomainRuleViolation e = assertThrows(DomainRuleViolation.class, courier::goOffDuty);
        assertEquals("Courier cannot go off duty while carrying or delivering an order", e.getMessage());
        assertEquals(CourierStatus.CARRYING, courier.getStatus());
    }

    @Test 
    public void goOffDutyWhileAssigned(){
        //Courier is allowed to go off duty when assigned!
        Courier courier = new Courier("1234", "Darcy", 60.6, 50.5, CourierStatus.ASSIGNED);
        courier.goOffDuty();
        assertEquals(CourierStatus.OFFLINE, courier.getStatus());

    }

    @Test 
    public void goOffDutyWhileOffline(){
        Courier courier = new Courier("1234", "Darcy", 60.6, 50.5, CourierStatus.OFFLINE);
        DomainRuleViolation e = assertThrows(DomainRuleViolation.class, courier::goOffDuty);
        assertEquals("Courier is already offline/off duty", e.getMessage());
        assertEquals(CourierStatus.OFFLINE, courier.getStatus());
    }

    @Test 
    public void goOffDutyAppropriately(){
        Courier courier = new Courier("1234", "Darcy", 60.6, 50.5, CourierStatus.IDLE);
        courier.goOffDuty();
        assertEquals(CourierStatus.OFFLINE, courier.getStatus());
    }

    @Test 
    public void goOnDutyAppropriately(){
        Courier courier = new Courier("1234", "Darcy", 60.6, 50.5, CourierStatus.OFFLINE);
        courier.goOnDuty();
        assertEquals(CourierStatus.IDLE, courier.getStatus());

    }
}

