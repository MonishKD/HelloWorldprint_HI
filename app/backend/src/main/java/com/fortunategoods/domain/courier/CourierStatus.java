package com.fortunategoods.domain.courier;
import com.fortunategoods.domain.order.DomainRuleViolation;

// The four courier states (PH3 5.4 DLV), the same names the couriers table allows (schema/03-users.sql).
public enum CourierStatus {
    OFFLINE, IDLE, ASSIGNED, CARRYING;

    // Checks if current courier's state is ASSIGNED for pickup
    public void requirePickupAllowed(){
        if (this != ASSIGNED){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Only an assigned courier can confirm pickup");
        }
    }
    
    // Check if current courier's state is CARRYING for delivery
    public void requireDeliveryAllowed() {
        if (this != CARRYING) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Only a carrying courier can confirm delivery");
        }
    }

}
