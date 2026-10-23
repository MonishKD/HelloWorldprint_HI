package com.fortunategoods.technical.security.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.user.User;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.UserRepository;
import com.fortunategoods.technical.security.SessionValidator;

// CurrentAccount (#66): the account behind the session, plus the restaurant for restaurant staff.
public class CurrentAccountTest {

    private SessionValidator sessions;
    private UserRepository users;
    private CurrentAccount currentAccount;

    @BeforeEach
    void setup() {
        sessions = mock(SessionValidator.class);
        users = mock(UserRepository.class);
        currentAccount = new CurrentAccount(sessions, users);
    }

    @Test
    void customerHasNoRestaurant() {
        when(sessions.currentSession()).thenReturn(new Session(5, UserRole.CUSTOMER));
        when(users.findById("5")).thenReturn(new User("5", "alice", "alice@example.com", null, "hash", UserRole.CUSTOMER));

        CurrentAccount.Account account = currentAccount.find();

        assertEquals("alice", account.user().getUsername());
        assertNull(account.restaurantId());
        verify(users, never()).findRestaurantIdByUserId(anyString());
    }

    @Test
    void restaurantStaffGetTheirRestaurant() {
        when(sessions.currentSession()).thenReturn(new Session(8, UserRole.RESTAURANT));
        when(users.findById("8")).thenReturn(new User("8", "pouletrouge", "pouletrouge@example.com", null, "hash", UserRole.RESTAURANT));
        when(users.findRestaurantIdByUserId("8")).thenReturn("1");

        assertEquals("1", currentAccount.find().restaurantId());
    }
}
