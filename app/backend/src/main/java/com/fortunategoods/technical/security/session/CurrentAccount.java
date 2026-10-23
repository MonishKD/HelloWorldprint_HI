package com.fortunategoods.technical.security.session;

import org.springframework.stereotype.Component;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.user.User;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.UserRepository;
import com.fortunategoods.technical.security.SessionValidator;

// Who is signed in (#66): the account behind the session cookie, for GET /api/auth/session.
// Restaurant staff also get their restaurant, which every restaurant read and command is scoped to.
//
// @Component makes Spring create one of these at startup and give it to AuthenticationService.
@Component
public class CurrentAccount {

    private final SessionValidator sessionValidator;
    private final UserRepository userRepository;

    public CurrentAccount(SessionValidator sessionValidator, UserRepository userRepository) {
        this.sessionValidator = sessionValidator;
        this.userRepository = userRepository;
    }

    // Not signed in: SessionValidator throws, and Spring Security answers 401.
    public Account find() {
        Session session = sessionValidator.currentSession();
        String userId = String.valueOf(session.getUserID());
        User user = userRepository.findById(userId);
        String restaurantId = session.getRole() == UserRole.RESTAURANT ? userRepository.findRestaurantIdByUserId(userId) : null;
        return new Account(user, restaurantId);
    }

    // restaurantId is null for every role but RESTAURANT.
    public record Account(User user, String restaurantId) {
    }
}
