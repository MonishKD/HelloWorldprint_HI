package com.fortunategoods.technical.security;

import org.springframework.stereotype.Component;

import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.user.User;
import com.fortunategoods.technical.security.registration.CourierRegistration;
import com.fortunategoods.technical.security.registration.CustomerRegistration;
import com.fortunategoods.technical.security.registration.RestaurantRegistration;
import com.fortunategoods.technical.security.session.Login;
import com.fortunategoods.technical.security.session.Logout;

// The one class RegController (#57, #62) calls for registration, login and logout, as in A2 (#28).
// It does no work itself: each method hands the request to the class that does that one job.
//   registerCustomer()    -> registration/CustomerRegistration
//   registerCourier()     -> registration/CourierRegistration
//   registerRestaurant()  -> registration/RestaurantRegistration   (restaurant owner, #143)
//   login()               -> session/Login
//   logout()              -> session/Logout
// So RegController never touches passwords, the database or the session.
// There is no Operator sign-up: Operators only come from the seed file (#25).
//
// @Component makes Spring create one of these at startup and give it to RegController.
// Spring fills the constructor with the five classes below, which are @Components too.
@Component
public class AuthenticationService {

    private final CustomerRegistration customerRegistration;
    private final CourierRegistration courierRegistration;
    private final RestaurantRegistration restaurantRegistration;
    private final Login login;
    private final Logout logout;

    public AuthenticationService(CustomerRegistration customerRegistration, CourierRegistration courierRegistration,
            RestaurantRegistration restaurantRegistration, Login login, Logout logout) {
        this.customerRegistration = customerRegistration;
        this.courierRegistration = courierRegistration;
        this.restaurantRegistration = restaurantRegistration;
        this.login = login;
        this.logout = logout;
    }

    // Each sign-up returns the new account with its id.
    // A taken username or email throws DuplicateKeyException (#60 makes it a 409).

    public User registerCustomer(String username, String email, String password) {
        return customerRegistration.register(username, email, password);
    }

    public User registerCourier(String username, String email, String password) {
        return courierRegistration.register(username, email, password);
    }

    // Account, restaurant and owner link, all saved or none. Returns the owner's account.
    public User registerRestaurant(String username, String email, String password, Restaurant newRestaurant,
            String address) {
        return restaurantRegistration.register(username, email, password, newRestaurant, address);
    }

    // Logs in with a username or email and a password, and returns the account.
    // A wrong one throws BadCredentialsException (#62 and #65 refuse the login with it).
    public User login(String usernameOrEmail, String password) {
        return login.login(usernameOrEmail, password);
    }

    // Ends the session. RegController calls it from POST /api/auth/logout (#62, #65).
    public void logout() {
        logout.logout();
    }
}
