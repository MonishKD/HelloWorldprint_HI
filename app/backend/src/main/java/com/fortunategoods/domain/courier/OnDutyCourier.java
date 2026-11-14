package com.fortunategoods.domain.courier;

// A courier shown on the city map: anyone who is not offline.
public record OnDutyCourier(String id, String name, double latitude, double longitude, CourierStatus status) {}
