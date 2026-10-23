package com.fortunategoods.application.reg;

import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.user.User;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.security.AuthenticationService;
import com.fortunategoods.technical.security.SecurityConfig;
import com.fortunategoods.technical.security.SecurityErrorResponses;
import com.fortunategoods.technical.security.session.CurrentAccount;
import com.fortunategoods.technical.security.session.LoggedInUser;

// The /api/auth routes (#60, #62, #65, #66) through the real security rules.
// AuthenticationService is a mock: these tests check what RegController answers, not how accounts are saved.
@WebMvcTest(
    controllers = RegController.class,
    properties = {
        "app.cors.allowed-origin=http://localhost:5173",
        "spring.application.name=fortunategoods-test"
    }
)
@Import({ SecurityConfig.class, SecurityErrorResponses.class })
public class RegControllerRoutesTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthenticationService authenticationService;

    // ---------- POST /api/auth/register (#60) ----------

    @Test
    void customerRegistersWithoutBeingSignedIn() throws Exception {
        when(authenticationService.registerCustomer("alice", "alice@example.com", "secret1")).thenReturn(alice());

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"email\":\"alice@example.com\",\"password\":\"secret1\",\"role\":\"customer\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.userId").value("5"))
        .andExpect(jsonPath("$.username").value("alice"))
        .andExpect(jsonPath("$.role").value("CUSTOMER"))
        .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void restaurantRegistersWithItsNameAndAddress() throws Exception {
        when(authenticationService.registerRestaurant(any(), any(), any(), any(Restaurant.class), any()))
        .thenReturn(new User("6", "bistro", "bistro@example.com", null, "hash", UserRole.RESTAURANT));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"bistro\",\"email\":\"bistro@example.com\",\"password\":\"secret1\",\"role\":\"restaurant\","
                        + "\"restaurantName\":\"Bistro\",\"address\":\"1 Main St\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.role").value("RESTAURANT"));
    }

    @Test
    void operatorRegistrationIsRefused() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"op\",\"email\":\"op@example.com\",\"password\":\"secret1\",\"role\":\"operator\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID"))
        .andExpect(jsonPath("$.message").value("Operator accounts cannot be registered"));

        verifyNoInteractions(authenticationService);
    }

    @Test
    void takenUsernameOrEmailIsAConflict() throws Exception {
        when(authenticationService.registerCustomer("alice", "alice@example.com", "secret1"))
        .thenThrow(new DuplicateKeyException("users_username_key"));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"email\":\"alice@example.com\",\"password\":\"secret1\",\"role\":\"customer\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    // ---------- POST /api/auth/login and /logout (#62, #65) ----------

    @Test
    void loginAnswersTheRole() throws Exception {
        when(authenticationService.login("alice", "demo123")).thenReturn(alice());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernameOrEmail\":\"alice\",\"password\":\"demo123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void wrongPasswordIsRefused() throws Exception {
        when(authenticationService.login("alice", "nope")).thenThrow(new BadCredentialsException("bad"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernameOrEmail\":\"alice\",\"password\":\"nope\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        mvc.perform(post("/api/auth/logout").with(loggedIn(UserRole.CUSTOMER)).with(csrf()))
        .andExpect(status().isNoContent());

        verify(authenticationService).logout();
    }

    // ---------- GET /api/auth/session (#66) ----------

    @Test
    void sessionSaysWhoIsSignedIn() throws Exception {
        when(authenticationService.currentAccount()).thenReturn(new CurrentAccount.Account(
                new User("8", "pouletrouge", "pouletrouge@example.com", null, "hash", UserRole.RESTAURANT), "1"));

        mvc.perform(get("/api/auth/session").with(loggedIn(UserRole.RESTAURANT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value("8"))
        .andExpect(jsonPath("$.username").value("pouletrouge"))
        .andExpect(jsonPath("$.role").value("restaurant"))
        .andExpect(jsonPath("$.restaurantId").value("1"));
    }

    @Test
    void sessionWithoutSignInIsRefused() throws Exception {
        mvc.perform(get("/api/auth/session"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(authenticationService);
    }

    private User alice() {
        return new User("5", "alice", "alice@example.com", null, "hash", UserRole.CUSTOMER);
    }

    private RequestPostProcessor loggedIn(UserRole role) {
        var login = UsernamePasswordAuthenticationToken.authenticated(new LoggedInUser(8, role), null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(login);
    }
}
