package com.fortunategoods.application.reg;

import com.fortunategoods.domain.user.UserRole;

/*
    What a successful POST /api/auth/register sends back to the frontend (#60): the new account, without its password.
    Spring turns it into JSON through the getters, e.g.
      { "userId": "7", "username": "ann", "email": "ann@mail.com", "role": "CUSTOMER" }
*/
public class RegisterResponse {

    private String userId;
    private String username;
    private String email;
    private UserRole role;

    public RegisterResponse(String userId, String username, String email, UserRole role) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.role = role;
    }

    // Copies the safe details out of a successful RegistrationResult (#57).
    public static RegisterResponse of(RegistrationResult result) {
        return new RegisterResponse(result.userId(), result.username(), result.email(), result.role());
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public UserRole getRole() {
        return role;
    }
}
