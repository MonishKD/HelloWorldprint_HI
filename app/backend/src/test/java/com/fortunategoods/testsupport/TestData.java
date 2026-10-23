package com.fortunategoods.testsupport;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.domain.user.User;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.UserRepository;
import com.fortunategoods.technical.security.role.RoleCheck;
import com.fortunategoods.technical.security.session.LoggedInUser;
import com.fortunategoods.technical.security.session.Login;
import com.fortunategoods.technical.security.session.SpringSecuritySessionValidator;

/*
    Test data shared by the test classes. Everything is created fresh on each call,
    so one test can't change what another test gets (MenuItem and Order are mutable).
*/
public final class TestData {
    private TestData() {}

    public static final String PASSWORD = "secret";
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    // Item "10" of restaurant "1": Wrap at 4.50, discounted from 9.00.
    public static MenuItem wrap() {
        return new MenuItem("10", "1", "Wrap", MenuItemStatus.AVAILABLE, 4.50, 9.00);
    }

    // Order of customer "7" at restaurant "1": 2 x Wrap, instructions "No onions".
    public static Order order(String id, OrderStatus status) {
        return order(id, status, null);
    }

    public static Order order(String id, OrderStatus status, String rejectReason) {
        return new Order(id, "7", "1", null, status, "No onions", null, null, null, rejectReason,
                List.of(OrderLine.fromMenuItem(wrap(), 2)));
    }

    // Signs a MockMvc request in as this user, the way Login stores it in the session.
    public static RequestPostProcessor loggedIn(long userId, UserRole role) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(new LoggedInUser(userId, role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    // The real Login, backed by a mocked accounts table that only knows "tester" (id 1, this role, password PASSWORD).
    public static Login login(UserRole role) {
        User account = new User("1", "tester", "tester@mail.com", null, PASSWORD_ENCODER.encode(PASSWORD), role);
        UserRepository accounts = mock(UserRepository.class);
        when(accounts.findByUsername("tester")).thenReturn(account);
        return new Login(accounts, PASSWORD_ENCODER);
    }

    // Logs "tester" in through the real Login on a fake request and returns their Session.
    // Tests that use it must clear SecurityContextHolder and RequestContextHolder afterwards.
    public static Session logIn(UserRole role) {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest(), new MockHttpServletResponse()));
        login(role).login("tester", PASSWORD);
        return new SpringSecuritySessionValidator(new RoleCheck()).currentSession();
    }
}
