package com.fortunategoods.technical.security.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.security.role.RoleCheck;
import static com.fortunategoods.testsupport.TestData.PASSWORD;
import static com.fortunategoods.testsupport.TestData.login;

// Story #15: logging in starts a session and logging out ends it. Uses the real Login, Logout and SessionValidator.
// Each "request" is a MockHttpServletRequest; giving the next one the same MockHttpSession
// plays the part of the browser's session cookie.
class SessionLifecycleTest {

    private final SpringSecuritySessionValidator sessionValidator = new SpringSecuritySessionValidator(new RoleCheck());
    private final HttpSessionSecurityContextRepository sessionStore = new HttpSessionSecurityContextRepository();
    private final Login login = login(UserRole.CUSTOMER);
    private final MockHttpSession browserSession = new MockHttpSession();

    @AfterEach
    void forgetLoggedInUser() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void loginPutsIdAndRoleInTheSession() {
        newRequest();
        login.login("tester", PASSWORD);

        newRequest();
        assertEquals(new Session(1, UserRole.CUSTOMER), sessionValidator.currentSession());
    }

    @Test
    void wrongPasswordStartsNoSession() {
        newRequest();
        assertThrows(BadCredentialsException.class, () -> login.login("tester", "wrong"));

        newRequest();
        assertThrows(AuthenticationException.class, sessionValidator::currentSession);
    }

    @Test
    void logoutEndsTheSession() {
        newRequest();
        login.login("tester", PASSWORD);
        newRequest();
        new Logout().logout();

        newRequest();
        assertThrows(AuthenticationException.class, sessionValidator::currentSession);
    }

    // Starts a new request carrying the browser's session and loads whoever is saved in it,
    // which is what Spring Security does at the start of every real request.
    private void newRequest() {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(browserSession);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()));
        SecurityContextHolder.setContext(sessionStore.loadDeferredContext(request).get());
    }
}
