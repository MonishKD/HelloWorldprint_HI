package com.fortunategoods.domain.courier;

// The four courier states (PH3 5.4 DLV), the same names the couriers table allows (schema/03-users.sql).
public enum CourierStatus {
    OFFLINE, IDLE, ASSIGNED, CARRYING
}
