package com.fortunategoods.domain.user;

// Where a new account is. name and deliveryAddress are only used for customers.
public record SignUpLocation(Double latitude, Double longitude, String name, String deliveryAddress) {
}
