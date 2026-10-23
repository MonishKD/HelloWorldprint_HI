package com.fortunategoods.application.reg;

import com.fortunategoods.domain.user.User;
import com.fortunategoods.domain.user.UserRole;

public record RegistrationResult(String userId, String username, String email, UserRole role) {

    // Builds the result from the account AuthenticationService saved.
    public static RegistrationResult of(User account) {
        return new RegistrationResult(account.getId(), account.getUsername(), account.getEmail(), account.getRole());
    }
}
