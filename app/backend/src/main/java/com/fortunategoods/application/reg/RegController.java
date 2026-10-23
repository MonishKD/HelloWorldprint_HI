package com.fortunategoods.application.reg;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.dao.DuplicateKeyException;

import com.fortunategoods.application.reg.RegistrationResult.RegistrationOutcome;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.user.SignUpRules;
import com.fortunategoods.domain.user.User;
import com.fortunategoods.domain.user.UserRole;

import com.fortunategoods.technical.security.AuthenticationService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.fortunategoods.application.ord.execution.CommandError;

/*  
Login and logout orchestration for #62, against AuthenticationService (#28).
 This has now been refactored for step #65 too!
 Registration (#57) is the register() method below.
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

    /*
        The registration URL (#60): POST /api/auth/register.
        SecurityConfig already lets anyone call it without being logged in.

        @RequestBody tells Spring to turn the JSON the frontend sends into a RegisterReqJSON (its own file).
        The real work is done by register() just below (#57); this method only translates its answer into HTTP:
          SUCCESS        -> 201 Created, with the new account (no password)
          INVALID_INPUT  -> 400 Bad Request, in the shared error shape { status, code, message }
          ACCOUNT_TAKEN  -> 409 Conflict, in the same shared error shape
    */
    @PostMapping("/register")
    public ResponseEntity<?> registerAccount(@RequestBody RegisterReqJSON request) {
        RegistrationResult result = register(request.getUsername(), request.getEmail(), request.getPassword(),
                request.getRole(), request.getRestaurantName(), request.getAddress());
        return switch (result.outcome()) {
            case SUCCESS -> ResponseEntity.status(HttpStatus.CREATED).body(RegisterResponse.of(result));
            case INVALID_INPUT -> error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", result.message());
            case ACCOUNT_TAKEN -> error(HttpStatus.CONFLICT, "ACCOUNT_TAKEN", result.message());
        };
    }

    /*
        Registration (#57): signs up one account with one public role.
        This is a plain method on purpose: the HTTP route (POST /api/auth/register) and the JSON body
        are added by #60, which will call this method and turn the result into a response.

        The steps, in order:
          1. Check the fields typed in the form are there and not too long.
          2. Ask the Domain's sign-up rules (SignUpRules) which role this is, and whether the
             restaurant name and address fit that role. Operator is refused here.
          3. Hand the account to AuthenticationService, which hashes the password and saves it.
             For a restaurant, it also saves the new closed restaurant and the owner link, all together (#143).
        Any refusal comes back as a RegistrationResult failure with a message, never as a crash.
    */
    public RegistrationResult register(String username, String email, String password, String role,
            String restaurantName, String address) {

        // 1. Required fields. 255 matches the limit used for login above.
        if (isBlank(username) || isBlank(email) || isBlank(password)) {
            return RegistrationResult.failure(RegistrationOutcome.INVALID_INPUT,
                    "Username, email and password are required.");
        }
        if (username.length() > 255 || email.length() > 255 || password.length() > 255) {
            return RegistrationResult.failure(RegistrationOutcome.INVALID_INPUT,
                    "Username, email and password must be at most 255 characters.");
        }

        // Spaces around the username or email are typing mistakes, so they are not saved.
        // The email is also saved in lower case, so "Ann@Mail.com" and "ann@mail.com" count as the same email.
        String cleanUsername = username.trim();
        String cleanEmail = email.trim().toLowerCase();

        try {
            // 2. The Domain's rules. Each one throws a DomainRuleViolation if the input breaks it.
            UserRole chosenRole = SignUpRules.publicRole(role);
            SignUpRules.checkRestaurantDetails(chosenRole, restaurantName, address);

            // 3. Save the account the way its role needs.
            User newAccount = saveAccount(chosenRole, cleanUsername, cleanEmail, password, restaurantName, address);
            // A restaurant account also gets back the id of the restaurant just created for it (#144).
            String restaurantId = null;
            if (chosenRole == UserRole.RESTAURANT) {
                restaurantId = authenticationService.restaurantIdOf(newAccount.getId());
            }
            return RegistrationResult.success(newAccount, restaurantId);

        } catch (DomainRuleViolation brokenRule) {
            // The message was written by SignUpRules for the user, e.g. "Operator accounts cannot be registered".
            return RegistrationResult.failure(RegistrationOutcome.INVALID_INPUT, brokenRule.getMessage());

        } catch (DuplicateKeyException taken) {
            // AuthenticationService throws this when the username or email is already used.
            // Its message says which one, e.g. "This username is already taken".
            return RegistrationResult.failure(RegistrationOutcome.ACCOUNT_TAKEN, taken.getMessage());
        }
    }

    // Picks the AuthenticationService method for the role. SignUpRules.publicRole() already refused OPERATOR,
    // so only these three roles can reach this method.
    private User saveAccount(UserRole role, String username, String email, String password,
            String restaurantName, String address) {
        switch (role) {
            case CUSTOMER:
                return authenticationService.registerCustomer(username, email, password);
            case COURIER:
                return authenticationService.registerCourier(username, email, password);
            case RESTAURANT:
                // The Domain builds the new restaurant: closed, with an empty menu (#144).
                Restaurant newRestaurant = SignUpRules.newRestaurant(restaurantName);
                return authenticationService.registerRestaurant(username, email, password, newRestaurant,
                        address.trim());
            default:
                throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID,
                        "Operator accounts cannot be registered");
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

    public record SessionResponse(UserRole role) {
    }}