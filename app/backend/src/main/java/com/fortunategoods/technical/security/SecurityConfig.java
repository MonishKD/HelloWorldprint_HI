package com.fortunategoods.technical.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

// Spring Security setup (#27).
// Every request from the frontend goes through these rules BEFORE it reaches a controller.
// If a request breaks a rule, Spring Security answers it by itself and our code never runs.
//
// The Spring words used in this file:
//   @Configuration   this class gives Spring setup objects. Spring runs its @Bean methods once, at startup.
//   @Bean            Spring keeps the object the method returns and uses it for the whole app.
//   rules -> rules   a lambda: Spring hands us a rule builder (here called "rules") and we add our rules to it.
@Configuration
public class SecurityConfig {

    // The only two requests that work without being logged in. Everything else needs a session (R-REG-02).
    private static final String REGISTER_URL = "/api/auth/register";
    private static final String LOGIN_URL = "/api/auth/login";

    // The header our frontend sends the CSRF token back in. This is the name Spring expects by default.
    private static final String CSRF_TOKEN_HEADER = "X-XSRF-TOKEN";

    // The security rules for every request.
    // Spring calls this list of checks a "filter chain": each request goes through the checks one by one.
    @Bean
    public SecurityFilterChain securityRules(HttpSecurity httpSecurity) throws Exception {

        // 1. Who needs to be logged in.
        //    Register and login are open to everyone.
        //    Every other URL needs a logged-in user, browsing menus included (R-REG-02: no guest access).
        httpSecurity.authorizeHttpRequests(urlRules -> urlRules
                .requestMatchers(HttpMethod.POST, REGISTER_URL, LOGIN_URL).permitAll()
                .anyRequest().authenticated());

        // 2. CORS: lets our frontend talk to the backend.
        //    The frontend (localhost:5173) and the backend (localhost:8080) count as two different websites,
        //    and a browser blocks one website from reading another's answers unless the backend allows it.
        //    withDefaults() means: use the rules from corsConfigurationSource() further down in this file.
        httpSecurity.cors(Customizer.withDefaults());

        // 3. CSRF: stops OTHER websites from acting as a logged-in user.
        //    The browser sends the session cookie with every request, even ones another website triggers.
        //    So Spring gives the browser a secret token in the XSRF-TOKEN cookie, and our frontend sends it back
        //    in the X-XSRF-TOKEN header. Other websites can't read our cookie, so they can't send the token,
        //    and their requests are refused.
        //    spa() is Spring's ready-made setup for this when the frontend is a single-page app, like our React one.
        //    Register and login skip the check: before its first request, the browser doesn't have a token yet.
        httpSecurity.csrf(csrfRules -> csrfRules
                .spa()
                .ignoringRequestMatchers(REGISTER_URL, LOGIN_URL));

        // Turns the rules above into the filter chain Spring uses.
        return httpSecurity.build();
    }

    // The CORS rules: which website may call the backend, and what it may send.
    // Keep this method's name: Spring finds these rules by the name "corsConfigurationSource".
    // frontendAddress is app.cors.allowed-origin in application.properties (http://localhost:5173 for now).
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origin}") String frontendAddress) {
        CorsConfiguration frontendRules = new CorsConfiguration();

        // Only our frontend may call the backend.
        frontendRules.setAllowedOrigins(List.of(frontendAddress));

        // The kinds of requests the frontend sends.
        frontendRules.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));

        // The headers the frontend sends: Content-Type for its JSON bodies, and the CSRF token.
        frontendRules.setAllowedHeaders(List.of("Content-Type", CSRF_TOKEN_HEADER));

        // Lets the browser send the session cookie along. Without this, nobody would ever look logged in.
        frontendRules.setAllowCredentials(true);

        // Use these rules for every URL of the backend ("/**" means all of them).
        UrlBasedCorsConfigurationSource rulesForEveryUrl = new UrlBasedCorsConfigurationSource();
        rulesForEveryUrl.registerCorsConfiguration("/**", frontendRules);
        return rulesForEveryUrl;
    }
}
