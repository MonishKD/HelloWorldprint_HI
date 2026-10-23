package com.fortunategoods.application.reg;

/*
    The body the frontend sends to POST /api/auth/register (#60).
    Example: { "username": "ann", "email": "ann@mail.com", "password": "secret", "role": "customer" }
    A restaurant account also sends "restaurantName" and "address".
    Spring (Jackson) fills each field from the JSON key with the same name, through the setters below.
    A key that isn't sent stays null, and RegController.register() refuses it with a 400.
*/
public class RegisterReqJSON {
    private String username;
    private String email;
    private String password;
    // A String on purpose, not a UserRole: an unknown role like "chef" then reaches SignUpRules,
    // which answers with a clear message instead of Spring failing to read the JSON.
    private String role;
    private String restaurantName; // restaurant accounts only
    private String address;        // restaurant accounts only

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getRestaurantName() {
        return restaurantName;
    }

    public void setRestaurantName(String restaurantName) {
        this.restaurantName = restaurantName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    // Never print the password in logs.
    @Override
    public String toString() {
        return "RegisterReqJSON[username=" + username + ", email=" + email + ", password=***, role=" + role
                + ", restaurantName=" + restaurantName + ", address=" + address + "]";
    }
}
