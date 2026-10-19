package com.fortunategoods.application.reg;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fortunategoods.domain.user.User;
import com.fortunategoods.domain.user.UserRole;

import com.fortunategoods.technical.security.AuthenticationService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/*  
Login and logout orchestration for #62, against AuthenticationService (#28).
 This has now been refactored for step #65 too!
*/ 
@RestController
@RequestMapping("/api/auth")
public class RegController {

    private final AuthenticationService authenticationService;

    public RegController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        LoginResult result = authenticate(request.usernameOrEmail(), request.password());
        return switch (result.outcome()) {
            case SUCCESS -> ResponseEntity.ok(new SessionResponse(result.role()));
            case INVALID_INPUT -> error(HttpStatus.BAD_REQUEST, "INVALID_INPUT",
                    "Username or email and password are required.");
            case INVALID_CREDENTIALS -> error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "Wrong username, email or password.");
        };
    }

    private LoginResult authenticate(String usernameOrEmail, String password) {
        if (isBlank(usernameOrEmail) || isBlank(password) || usernameOrEmail.length() > 255 || password.length() > 255) {
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

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        authenticationService.logout();
        return ResponseEntity.noContent().build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(new ErrorResponse(code, message));
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

    public record LoginRequest(String usernameOrEmail, String password) {
    @Override
    public String toString() {
        return "LoginRequest[usernameOrEmail=" + usernameOrEmail + ", password=***]";
        }
    }

    public record SessionResponse(UserRole role) {
    }

    // Placeholder: replace with the project's shared error shape.
    public record ErrorResponse(String error, String message) {
    }
}