package com.fortunategoods.technical.security.registration;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fortunategoods.domain.user.User;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.UserRepository;

// What every sign-up does, whatever the role (#28):
// refuse a taken username or email, hash the password, save the account.
// CustomerRegistration, CourierRegistration and RestaurantRegistration all build on it, so this part is written only once.
//
// "abstract" means nobody creates a Registration directly, only one of the two registrations that "extends" it.
public abstract class Registration {

    // "protected" means the registrations that extend this class can use it too (RestaurantRegistration does).
    protected final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // The two registrations hand these over with super(...) in their own constructor.
    protected Registration(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Saves a new account with its password hashed, and returns it with its new id.
    // A taken username or email throws DuplicateKeyException, the same error the database gives,
    // so the registration endpoint (#60) only has one error to turn into a 409.
    // "protected" means only the registrations that extend this class can call it.
    protected User saveAccount(String username, String email, String password, UserRole role) {
        if (userRepository.findByUsername(username) != null) {
            throw new DuplicateKeyException("This username is already taken");
        }
        if (userRepository.findByEmail(email) != null) {
            throw new DuplicateKeyException("This email is already used by another account");
        }

        // Only the hash is saved, never the password.
        String passwordHash = passwordEncoder.encode(password);

        // id is null until the database makes one. Phase II has no phone number.
        User newUser = new User(null, username, email, null, passwordHash, role);
        String newId = userRepository.add(newUser);

        return new User(newId, username, email, null, passwordHash, role);
    }
}
