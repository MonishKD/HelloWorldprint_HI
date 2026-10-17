package com.fortunategoods.technical.security.session;

import java.util.Set;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.security.SessionValidator;
import com.fortunategoods.technical.security.role.RoleCheck;

// The real SessionValidator (#27). Answers "valid session, which role?" from the Spring Security session.
// CommandInvoker (#68) calls it before every command, and GET /api/auth/me (#66) will use currentSession().
//
// Where the logged-in user comes from:
//   Every request sends the session cookie. Before our code runs, Spring Security finds that session in memory
//   and puts what's saved in it into SecurityContextHolder, for this request only.
//   After a login (#28), that's a LoggedInUser. When nobody is logged in, Spring puts a placeholder
//   called "anonymousUser" there instead, so we check for a LoggedInUser.
//
// What the exceptions turn into:
//   AuthenticationException -> 401 (nobody logged in), AccessDeniedException -> 403 (not allowed).
//   CommandInvoker logs them in the CommandLog, then SecurityErrorResponses writes the JSON the browser gets.
//
// @Component makes Spring create one of these at startup and give it to CommandInvoker.
@Component
public class SpringSecuritySessionValidator implements SessionValidator {

    private static final String NOT_LOGGED_IN_MESSAGE = "Sign-in again to perform this action";

    private final RoleCheck roleCheck;

    // Spring gives us the RoleCheck (#29), which is a @Component too.
    public SpringSecuritySessionValidator(RoleCheck roleCheck) {
        this.roleCheck = roleCheck;
    }

    // Returns the logged-in user's id and role as a Session.
    // Throws (401) when nobody is logged in, or the session expired.
    @Override
    public Session currentSession() {
        Authentication springSession = SecurityContextHolder.getContext().getAuthentication();

        if (springSession == null || !(springSession.getPrincipal() instanceof LoggedInUser)) {
            throw new AuthenticationCredentialsNotFoundException(NOT_LOGGED_IN_MESSAGE);
        }

        LoggedInUser loggedInUser = (LoggedInUser) springSession.getPrincipal();
        return new Session(loggedInUser.userId(), loggedInUser.role());
    }

    // Checks that the Session given to CommandInvoker belongs to the user logged in on this request.
    // Throws (401) when nobody is logged in, or when the Session is someone else's.
    @Override
    public void requireValid(Session session) {
        Session loggedInSession = currentSession();

        if (!loggedInSession.equals(session)) {
            throw new AuthenticationCredentialsNotFoundException(NOT_LOGGED_IN_MESSAGE);
        }
    }

    // Checks that the logged-in user's role is one of the command's allowed roles (#29).
    // Throws (403) when it isn't. RoleCheck does the comparison.
    // The Session itself was already checked by requireValid(), which CommandInvoker calls first.
    @Override
    public void requireRoles(Session session, Set<UserRole> allowedRoles) {
        Authentication springSession = SecurityContextHolder.getContext().getAuthentication();
        roleCheck.requireOneOf(springSession, allowedRoles);
    }
}
