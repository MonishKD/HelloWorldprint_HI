package com.fortunategoods.technical.persistence;

// One user, the way it is saved in the users table.
// A "record" is a short way to write a class that only holds values: user.username(), user.email(), etc. are the getters.
// role is CUSTOMER, RESTAURANT, COURIER or OPERATOR.
public record UserAccount(String id, String username, String email, String phoneNumber, String passwordHash, String role) {
}
