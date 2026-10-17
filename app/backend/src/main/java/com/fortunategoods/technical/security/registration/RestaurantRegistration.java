package com.fortunategoods.technical.security.registration;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.user.User;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;

// Sign-up for a restaurant owner (#143): the account, their new restaurant and the owner link.
// The account part is the common one from Registration.
//
// @Component makes Spring create one of these at startup and give it to AuthenticationService.
@Component
public class RestaurantRegistration extends Registration {

    private final RestaurantRepository restaurantRepository;

    // super(...) hands the accounts table and the password hasher to Registration.
    public RestaurantRegistration(UserRepository userRepository, RestaurantRepository restaurantRepository,
            PasswordEncoder passwordEncoder) {
        super(userRepository, passwordEncoder);
        this.restaurantRepository = restaurantRepository;
    }

    // Saves the three together and returns the owner's account.
    // The Domain builds newRestaurant (#144: closed, empty menu). address is passed apart because Restaurant has no address.
    // Sign-up doesn't ask for a cuisine, so it stays empty.
    // @Transactional: if any of the three saves fails, none of them is kept.
    @Transactional
    public User register(String username, String email, String password, Restaurant newRestaurant, String address) {
        User owner = saveAccount(username, email, password, UserRole.RESTAURANT);
        String restaurantId = restaurantRepository.add(newRestaurant, null, address);
        // userRepository comes from Registration.
        userRepository.addRestaurantPersonnel(owner.getId(), restaurantId, true);
        return owner;
    }
}
