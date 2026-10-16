package com.fortunategoods.technical.security;

import com.fortunategoods.domain.user.UserRole;

// Who is logged in: what Spring Security keeps in the session (in server memory) after a login (#27).
// Login (#28) must create one of these and save it in the Spring Security session.
// SpringSecuritySessionValidator reads it back on every request.
//
// "record" is a short way to write a class that only holds values:
// Java writes the constructor and the getters (userId() and role()) for us.
public record LoggedInUser(long userId, UserRole role) {
}
