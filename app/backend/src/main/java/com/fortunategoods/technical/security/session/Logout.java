package com.fortunategoods.technical.security.session;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// Logout (#28). Ends the user's Spring Security session.
//
// @Component makes Spring create one of these at startup and give it to AuthenticationService.
@Component
public class Logout {

    // Spring Security's logout handler deletes the session and forgets who was logged in.
    // Every request after this gets a 401 until the next login.
    // Spring keeps the request being handled right now, so RegController doesn't have to pass it in.
    public void logout() {
        ServletRequestAttributes currentRequest = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        Authentication loggedIn = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(currentRequest.getRequest(), currentRequest.getResponse(), loggedIn);
    }
}
