package com.fortunategoods.technical.security.registration;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.fortunategoods.domain.user.User;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.UserRepository;

// Sign-up for a Courier (#28).
// The account part is the common one from Registration, saved with the COURIER role.
// UserRepository also adds the courier's row in the couriers table.
//
// @Component makes Spring create one of these at startup and give it to AuthenticationService.
@Component
public class CourierRegistration extends Registration {

    // super(...) hands the accounts table and the password hasher to Registration.
    public CourierRegistration(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        super(userRepository, passwordEncoder);
    }

    // Saves the courier's account and returns it with its new id.
    public User register(String username, String email, String password) {
        return saveAccount(username, email, password, UserRole.COURIER);
    }
}
