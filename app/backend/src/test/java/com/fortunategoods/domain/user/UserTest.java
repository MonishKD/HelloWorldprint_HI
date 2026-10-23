package com.fortunategoods.domain.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.fortunategoods.domain.order.DomainRuleViolation;

// #56: an account always has exactly one role.
class UserTest {

    @Test
    void accountKeepsItsOneRole() {
        User user = new User("1", "alice", "alice@example.com", null, "hash", UserRole.CUSTOMER);
        assertEquals(UserRole.CUSTOMER, user.getRole());
    }

    @Test
    void accountWithoutARoleIsRefused() {
        DomainRuleViolation e = assertThrows(DomainRuleViolation.class,
                () -> new User("1", "alice", "alice@example.com", null, "hash", null));
        assertEquals(DomainRuleViolation.Reason.INVALID, e.getReason());
    }
}
