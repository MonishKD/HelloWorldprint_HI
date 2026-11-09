package com.fortunategoods.technical.security.registration;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fortunategoods.domain.user.SignUpLocation;
import com.fortunategoods.domain.user.User;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.UserRepository;

// Sign-up for a Customer (#28).
// The account part is the common one from Registration, saved with the CUSTOMER role.
// UserRepository also adds the customer's row in the customers table.
//
// @Component makes Spring create one of these at startup and give it to AuthenticationService.
@Component
public class CustomerRegistration extends Registration {

    // super(...) hands the accounts table and the password hasher to Registration.
    public CustomerRegistration(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        super(userRepository, passwordEncoder);
    }

    // Saves the customer's account with their name and delivery location, all or none, and returns it with its new id.
    @Transactional
    public User register(String username, String email, String password, SignUpLocation location) {
        User customer = saveAccount(username, email, password, UserRole.CUSTOMER);
        userRepository.saveCustomerLocation(customer.getId(), location.name().trim(), location.deliveryAddress().trim(),
                location.latitude(), location.longitude());
        return customer;
    }
}
