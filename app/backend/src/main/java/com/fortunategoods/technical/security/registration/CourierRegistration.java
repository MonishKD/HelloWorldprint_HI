package com.fortunategoods.technical.security.registration;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fortunategoods.domain.courier.CourierStatus;
import com.fortunategoods.domain.user.SignUpLocation;
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

    // Saves the courier's account and position, all or none, and returns it with its new id. A new courier starts off duty.
    @Transactional
    public User register(String username, String email, String password, SignUpLocation location) {
        User courier = saveAccount(username, email, password, UserRole.COURIER);
        userRepository.saveCourierProfile(courier.getId(), username, location.latitude(), location.longitude(), CourierStatus.OFFLINE);
        return courier;
    }
}
