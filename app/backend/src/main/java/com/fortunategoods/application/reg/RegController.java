package com.fortunategoods.application.reg;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import com.fortunategoods.domain.user.User;
import com.fortunategoods.domain.user.UserRole;

import com.fortunategoods.technical.security.AuthenticationService;

/*  
Login and logout orchestration for #62, against AuthenticationService (#28).
 This currently holds no HTTP route bindings and it never touches
 passwords, the database or the session itself: AuthenticationService does all of that.
*/ 
@Component
public class RegController {

    private final AuthenticationService authenticationService;

    public RegController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    // Logs in with a username or email and a password.
    // Success: the outcome is SUCCESS and carries the user's role.
    // Failure: the outcome is INVALID_INPUT (missing field) or INVALID_CREDENTIALS (wrong login),
    // and no role is returned.
    public LoginResult login(String usernameOrEmail, String password) {
        if (isBlank(usernameOrEmail) || isBlank(password) || usernameOrEmail.isEmpty() || password.isEmpty()) {
            return LoginResult.failure(LoginOutcome.INVALID_INPUT);
        }
        try {
            User user = authenticationService.login(usernameOrEmail.trim(), password);
            return LoginResult.success(user.getRole());
        } catch (BadCredentialsException e) {
            // Same outcome for an unknown user and a wrong password, so neither is revealed.
            return LoginResult.failure(LoginOutcome.INVALID_CREDENTIALS);
        }
    }

    // Ends the session through AuthenticationService.logout(), which uses Spring Security's logout handler.
    public LogoutResult logout() {
        authenticationService.logout();
        return new LogoutResult(LogoutOutcome.LOGGED_OUT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // Here are the enums that show off the "clear response outcomes" as mentioned in the task description on GitHub
    public enum LoginOutcome {
        SUCCESS, INVALID_INPUT, INVALID_CREDENTIALS
    }

    public enum LogoutOutcome {
        LOGGED_OUT
    }

    /* We make sure that the role is set only when its outcome is determined to be a SUCCESS!
    otherwise, we make the result null. */ 
    public record LoginResult(LoginOutcome outcome, UserRole role) {

        static LoginResult success(UserRole role) {
            return new LoginResult(LoginOutcome.SUCCESS, role);
        }

        static LoginResult failure(LoginOutcome outcome) {
            return new LoginResult(outcome, null);
        }

        public boolean isSuccess() {
            return outcome == LoginOutcome.SUCCESS;
        }
    }

    public record LogoutResult(LogoutOutcome outcome) {
    }
}