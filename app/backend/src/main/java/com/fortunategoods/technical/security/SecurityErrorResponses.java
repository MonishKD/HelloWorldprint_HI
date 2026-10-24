package com.fortunategoods.technical.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fortunategoods.application.ord.execution.CommandError;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

// Writes the answer the browser gets when Spring Security refuses a request (#27):
//   401 UNAUTHENTICATED  nobody is logged in (no session, or it expired). The frontend sends the user back to login.
//   403 FORBIDDEN        someone is logged in but isn't allowed to do this (wrong role #29, or a bad CSRF token).
//
// Both use the same shape as every other error of the app, { "status", "code", "message" },
// because they reuse CommandError, the class OrderExceptionHandler sends for 400/404/409 (#71).
// The frontend shows "message" in the output console (R-DASH-06).
//
// How Spring uses this class:
//   Spring Security doesn't call our methods by name. It calls the two methods its interfaces define:
//   AuthenticationEntryPoint.commence() for a 401, and AccessDeniedHandler.handle() for a 403.
//   SecurityConfig tells Spring to use this class for both.
//   @Component makes Spring create one of these at startup so SecurityConfig can be given it.
@Component
public class SecurityErrorResponses implements AuthenticationEntryPoint, AccessDeniedHandler {

    // Same code and message CommandInvoker writes in the CommandLog for a 401, so the console and the response match.
    private static final String NOT_LOGGED_IN_CODE = "UNAUTHENTICATED";
    private static final String NOT_LOGGED_IN_MESSAGE = "Sign-in again to perform this action";

    private static final String NOT_ALLOWED_CODE = "FORBIDDEN";

    // Turns a Java object into JSON text. Spring Boot already has one set up, and gives it to us here.
    private final JsonMapper jsonMapper;

    public SecurityErrorResponses(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    // 401: Spring calls this when a request needs a logged-in user and there isn't one.
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException notLoggedIn) throws IOException {
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, NOT_LOGGED_IN_CODE, NOT_LOGGED_IN_MESSAGE);
    }

    // 403: Spring calls this when the user is logged in but the request is refused.
    // The message is the reason given by whoever refused it,
    // e.g. "Restaurant staff can only view their own restaurant's menu" from the Domain, through CommandInvoker.
    // Nobody logged in is still a 401, even when the CSRF check refused the request first
    // (a visitor with no session has no CSRF token either), so the frontend sends them back to login.
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException notAllowed) throws IOException {
        if (nobodyLoggedIn()) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, NOT_LOGGED_IN_CODE, NOT_LOGGED_IN_MESSAGE);
            return;
        }
        writeError(response, HttpServletResponse.SC_FORBIDDEN, NOT_ALLOWED_CODE, notAllowed.getMessage());
    }

    // Spring Security keeps who is logged in for this request in the SecurityContextHolder.
    // A visitor without a session shows up as nothing at all, or as Spring's "anonymous" user.
    private boolean nobodyLoggedIn() {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        return current == null || current instanceof AnonymousAuthenticationToken;
    }

    // Sets the status (401 or 403), says the body is JSON, and writes { "status", "code", "message" } as the body.
    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        CommandError error = new CommandError(status, code, message);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), error);
    }
}
