package com.fortunategoods.domain.user;

import com.fortunategoods.domain.order.DomainRuleViolation;

// One account with exactly one role (#56). The enum rules out unknown roles and the single field rules out several;
// the constructor rules out none.
public class User {

    private String id;
    private String username;
    private String email;
    private String phoneNumber;
    private String passwordHash;
    private UserRole role;

    public User(String id, String username, String email, String phoneNumber, String passwordHash, UserRole role) {
        if (role == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "An account needs a role");
        }
        this.id = id;
        this.username = username;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

}
