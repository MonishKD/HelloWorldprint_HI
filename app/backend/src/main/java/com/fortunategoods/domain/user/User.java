package com.fortunategoods.domain.user;

public class User {

    private String id;
    private String username;
    private String email;
    private String phoneNumber;
    private String passwordHash;
    private UserRole role;

    public User(String id, String username, String email, String phoneNumber, String passwordHash, UserRole role) {
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
