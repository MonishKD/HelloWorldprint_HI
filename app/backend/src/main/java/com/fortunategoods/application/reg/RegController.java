package com.fortunategoods.application.reg;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.user.SignUpRules;
import com.fortunategoods.domain.user.User;
import com.fortunategoods.domain.user.UserRole;

import com.fortunategoods.technical.security.AuthenticationService;
import com.fortunategoods.technical.security.session.CurrentAccount;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.fortunategoods.application.ord.execution.CommandError;

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

    // POST /api/auth/register (#60): { username, email, password, role, restaurantName?, address? }.
    // 201 with the new account (never the password). Registering doesn't log in: the frontend logs in right after.
    @PostMapping("/register")
    public ResponseEntity<?> registerAccount(@RequestBody RegisterRequest request) {
        try {
            RegistrationResult account = register(request.username(), request.email(), request.password(),
                    request.role(), request.restaurantName(), request.address());
            return ResponseEntity.status(HttpStatus.CREATED).body(account);
        } catch (DomainRuleViolation e) {
            return error(HttpStatus.BAD_REQUEST, "INVALID", e.getMessage());
        } catch (DuplicateKeyException e) {
            return error(HttpStatus.CONFLICT, "CONFLICT", "That username or email is already taken.");
        }
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

    // Registers a new account, checking the input and the Domain rules. Returns the new account's id, username, email and role.
    public RegistrationResult register(String username, String email, String password, String role,
            String restaurantName, String address) {
        // 1. Required fields
        requireFilled(username, "Username");
        requireFilled(email, "Email");
        requireFilled(password, "Password");

        // 2. Domain rule: one public role, never operator
        UserRole chosenRole = SignUpRules.publicRole(role);

        // 3. Restaurant name and address only for the restaurant role
        boolean hasRestaurantFields = filled(restaurantName) || filled(address);
        if (chosenRole == UserRole.RESTAURANT) {
            requireFilled(restaurantName, "Restaurant name");
            requireFilled(address, "Address");
        } else if (hasRestaurantFields) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID,
                    "Restaurant name and address are only for restaurant accounts");
        }

        // 4. Save the account through AuthenticationService, which hashes the password
        String cleanUsername = username.trim();
        String cleanEmail = email.trim();
        User account = switch (chosenRole) {
            case CUSTOMER -> authenticationService.registerCustomer(cleanUsername, cleanEmail, password);
            case COURIER -> authenticationService.registerCourier(cleanUsername, cleanEmail, password);
            case RESTAURANT -> {
                // The account, its new closed restaurant and the owner link are saved together (#28)
                Restaurant newRestaurant = SignUpRules.newRestaurant(restaurantName);
                yield authenticationService.registerRestaurant(cleanUsername, cleanEmail, password, newRestaurant, address.trim());
            }
            case OPERATOR -> throw new IllegalStateException("SignUpRules already refuses operators");
        };

        return RegistrationResult.of(account);
    }

    // Throws INVALID with "<field> is required" when the value is missing or only spaces.
    private static void requireFilled(String value, String field) {
        if (!filled(value)) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, field + " is required");
        }
    }

    // true when the value has something other than spaces.
    private static boolean filled(String value) {
        return value != null && !value.isBlank();
    }

    // GET /api/auth/session (#66): who the session cookie belongs to. The frontend calls it after login and on reload,
    // so it never decides the role itself. Not signed in: Spring Security answers 401 before this runs.
    @GetMapping("/session")
    public CurrentSessionResponse currentSession() {
        CurrentAccount.Account account = authenticationService.currentAccount();
        User user = account.user();
        return new CurrentSessionResponse(user.getId(), user.getUsername(), user.getEmail(),
                user.getRole().name().toLowerCase(java.util.Locale.ROOT), account.restaurantId());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        authenticationService.logout();
        return ResponseEntity.noContent().build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static ResponseEntity<CommandError> error(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(new CommandError(status.value(), code, message));
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

    public record RegisterRequest(String username, String email, String password, String role,
            String restaurantName, String address) {
        @Override
        public String toString() {
            return "RegisterRequest[username=" + username + ", email=" + email + ", password=***, role=" + role + "]";
        }
    }

    public record CurrentSessionResponse(String userId, String username, String email, String role, String restaurantId) {
    }

    public record SessionResponse(UserRole role) {
    }
}