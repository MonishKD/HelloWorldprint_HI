package com.fortunategoods.technical.security.session;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.context.request.*;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.user.*;
import com.fortunategoods.technical.persistence.UserRepository;
import com.fortunategoods.technical.security.role.RoleCheck;

// Story #15: logging in starts a session and logging out ends it.
// Uses the real Login, Logout and SessionValidator. Nothing here talks to a database.
//
// A "request" is faked with Spring's MockHttpServletRequest. The browser's session cookie is faked by
// giving the next request the same MockHttpSession, the way the cookie points the server to the same session.
class SessionLifecycleTest {

    private static final String PASSWORD = "secret";

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SpringSecuritySessionValidator sessionValidator = new SpringSecuritySessionValidator(new RoleCheck());

    // Where Spring Security keeps the logged-in user between requests (the server-side session).
    private final HttpSessionSecurityContextRepository sessionStore = new HttpSessionSecurityContextRepository();

    // One customer account with id 1. The fake accounts table always finds it.
    private final User account = new User("1", "tester", "tester@mail.com", null, passwordEncoder.encode(PASSWORD), UserRole.CUSTOMER);
    private final UserRepository accounts = new UserRepository(null) {
        @Override
        public User findByUsername(String username) {
            return username.equals("tester") ? account : null;
        }
    };

    private final Login login = new Login(accounts, passwordEncoder);

    // Clear the logged-in user so one test can't leak into the next.
    @AfterEach
    void forgetLoggedInUser() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    // After login, the user's id and role live in the session: the next request knows who they are.
    @Test
    void loginPutsIdAndRoleInTheSession() {
        MockHttpSession browserSession = new MockHttpSession();

        newRequest(browserSession);
        login.login("tester", PASSWORD);

        newRequest(browserSession);
        assertEquals(new Session(1, UserRole.CUSTOMER), sessionValidator.currentSession());
    }

    // A wrong password is refused and starts no session.
    @Test
    void wrongPasswordStartsNoSession() {
        MockHttpSession browserSession = new MockHttpSession();

        newRequest(browserSession);
        assertThrows(BadCredentialsException.class, () -> login.login("tester", "wrong"));

        newRequest(browserSession);
        assertThrows(AuthenticationException.class, sessionValidator::currentSession);
    }

    // After logout, the next request is no longer logged in (it would get a 401).
    @Test
    void logoutEndsTheSession() {
        MockHttpSession browserSession = new MockHttpSession();

        newRequest(browserSession);
        login.login("tester", PASSWORD);

        newRequest(browserSession);
        new Logout().logout();

        newRequest(browserSession);
        assertThrows(AuthenticationException.class, sessionValidator::currentSession);
    }

    // Starts a new fake request carrying this session, and loads whoever is saved in it,
    // which is what Spring Security does at the start of every real request.
    private void newRequest(MockHttpSession browserSession) {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(browserSession);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()));

        SecurityContextHolder.setContext(sessionStore.loadDeferredContext(request).get());
    }
}
