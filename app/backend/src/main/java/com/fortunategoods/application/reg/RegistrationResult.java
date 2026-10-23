//written by Monish. had a merge conflict issue with two RegControllers made by different people.
package com.fortunategoods.application.reg;

import com.fortunategoods.domain.user.User;
import com.fortunategoods.domain.user.UserRole;

/*
    What RegController.register() (#57) gives back: did the sign-up work, and if not, why.
    It is built the same way as LoginResult in RegController:
      - outcome  says what happened (SUCCESS, INVALID_INPUT or ACCOUNT_TAKEN).
      - message  is a sentence the frontend can show as-is when it failed (null on success).
      - userId, username, email, role  describe the new account, only on SUCCESS (null otherwise).
      - restaurantId  is the new restaurant's id for a restaurant account (null for the other roles).
    The password and its hash are never in here, so they can never be sent back to the browser.

    "record" is a short Java way to write a class that only holds values:
    Java writes the constructor and the getters (outcome(), userId()...) for us.
*/
public record RegistrationResult(RegistrationOutcome outcome, String message,
        String userId, String username, String email, UserRole role, String restaurantId) {

    // The three ways a sign-up can end.
    public enum RegistrationOutcome {
        SUCCESS,        // the account was saved
        INVALID_INPUT,  // something is missing or breaks a sign-up rule (e.g. operator role, no restaurant name)
        ACCOUNT_TAKEN   // the username or email already belongs to another account
    }

    // The sign-up worked: copy the safe details of the account AuthenticationService saved.
    public static RegistrationResult success(User account, String restaurantId) {
        return new RegistrationResult(RegistrationOutcome.SUCCESS, null,
                account.getId(), account.getUsername(), account.getEmail(), account.getRole(), restaurantId);
    }

    // The sign-up was refused: only the outcome and the reason, no account.
    public static RegistrationResult failure(RegistrationOutcome outcome, String message) {
        return new RegistrationResult(outcome, message, null, null, null, null, null);
    }

    public boolean isSuccess() {
        return outcome == RegistrationOutcome.SUCCESS;
    }
}
