package com.fortunategoods.technical.security;

import static org.mockito.ArgumentMatchers.any;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fortunategoods.domain.courier.CourierStatus;
import com.fortunategoods.domain.user.SignUpLocation;
import com.fortunategoods.technical.persistence.UserRepository;
import com.fortunategoods.technical.security.registration.CourierRegistration;
import com.fortunategoods.technical.security.registration.CustomerRegistration;

class SignUpLocationSavedTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    @BeforeEach
    void setup() {
        when(users.add(any())).thenReturn("5");
    }

    @Test
    void newCourierIsSavedOffDutyAtTheirPosition() {
        new CourierRegistration(users, encoder).register("carl", "carl@mail.com", "secret", new SignUpLocation(45.5, -73.6, null, null));

        verify(users).saveCourierProfile("5", "carl", 45.5, -73.6, CourierStatus.OFFLINE);
    }

    @Test
    void newCustomerIsSavedWithTheirNameAndAddress() {
        new CustomerRegistration(users, encoder).register("ann", "ann@mail.com", "secret", new SignUpLocation(45.5, -73.6, " Ann ", " 1 Main St "));

        verify(users).saveCustomerLocation("5", "Ann", "1 Main St", 45.5, -73.6);
    }
}
