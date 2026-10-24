package com.fortunategoods.technical.security.session;

import java.util.List;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fortunategoods.domain.user.User;
import com.fortunategoods.technical.persistence.UserRepository;

// Login with a username or email and a password (#28). Starts the user's Spring Security session.
//
// The Spring words used in this file:
//   @Component        Spring creates one of these at startup and gives it to AuthenticationService.
//   SecurityContext   Spring Security's box holding who is logged in. SessionValidator (#27) reads it on every request.
//   "implements UserDetailsService" means Spring Security's password checker can ask this class for an account,
//   through loadUserByUsername() further down.
@Component
public class Login implements UserDetailsService {

    private final UserRepository userRepository;

    // Spring Security's password checker.
    private final AuthenticationManager passwordChecker;

    // Saves the SecurityContext in the user's session (server memory), which their session cookie points to.
    private final HttpSessionSecurityContextRepository sessionStore = new HttpSessionSecurityContextRepository();

    // Spring gives us the accounts table (#24) and the BCrypt password hasher from SecurityConfig.
    public Login(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;

        // The password checker finds the account with this class's loadUserByUsername() ("this"),
        // then compares the typed password to the stored hash with passwordEncoder.
        DaoAuthenticationProvider accountsInDatabase = new DaoAuthenticationProvider(this);
        accountsInDatabase.setPasswordEncoder(passwordEncoder);
        this.passwordChecker = new ProviderManager(accountsInDatabase);
    }

    // Logs in, saves the user in their session, and returns the account.
    // A wrong username, email or password throws BadCredentialsException (#62 and #65 refuse the login with it).
    public User login(String usernameOrEmail, String password) {
        // 1. Spring Security checks the password, and throws if it's wrong.
        //    So the lines below only run for a right password.
        UsernamePasswordAuthenticationToken typedIn =
                UsernamePasswordAuthenticationToken.unauthenticated(usernameOrEmail, password);
        passwordChecker.authenticate(typedIn);

        // 2. Who is logged in: the account's id and role, as the LoggedInUser that SessionValidator reads (#27).
        User user = findByUsernameOrEmail(usernameOrEmail);
        LoggedInUser loggedInUser = new LoggedInUser(Long.parseLong(user.getId()), user.getRole());

        //    The role is also saved as the user's "authority": the label Spring Security checks to decide
        //    what they may do (#29). Spring's role labels start with "ROLE_", e.g. ROLE_CUSTOMER.
        SimpleGrantedAuthority roleAuthority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());
        Authentication loggedIn = UsernamePasswordAuthenticationToken.authenticated(loggedInUser, null, List.of(roleAuthority));

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(loggedIn);
        SecurityContextHolder.setContext(securityContext);

        // 3. Save it in the session, so the frontend's next requests (with the session cookie) are recognised.
        //    Spring Security doesn't do this by itself for a login endpoint we write ourselves.
        //    Spring keeps the request being handled right now, so RegController doesn't have to pass it in.
        ServletRequestAttributes currentRequest = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        sessionStore.saveContext(securityContext, currentRequest.getRequest(), currentRequest.getResponse());

        return user;
    }

    // Spring Security calls this during login to get the account's stored password hash.
    // Keep this method's name: Spring Security looks for "loadUserByUsername", even though we also accept an email.
    // It wants the account as its own "UserDetails" type, so we copy the username and hash into one.
    // (Written in full because Spring Security's class is also called User, like ours in domain.user.)
    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) {
        User user = findByUsernameOrEmail(usernameOrEmail);
        if (user == null) {
            throw new UsernameNotFoundException("No account with this username or email");
        }
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .build();
    }

    // Login accepts either: looks for the username first, then the email. Returns null if neither matches.
    private User findByUsernameOrEmail(String usernameOrEmail) {
        User user = userRepository.findByUsername(usernameOrEmail);
        if (user == null) {
            user = userRepository.findByEmail(usernameOrEmail);
        }
        return user;
    }
}
