package com.fortunategoods.technical.security;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.user.UserRole;
import java.util.Set;

/*
    Class that commandInvoker will call in the Technical Services layer to verify
    authentication and permissions
*/
public interface SessionValidator {

    // Returns the logged-in user's Session (id and role), read from the Spring session.
    // If nobody is logged in, throws AuthenticationException. Used to fill the Session and by GET /api/auth/me (#66).
    Session currentSession();

    // Checks session against Spring session, if it fails throws AuthenticationException
    void requireValid(Session session);

    // Checks session's roles with allowedRoles from command attempted at being
    // executed. If it fails, throws AccessDeniedException.
    void requireRoles(Session session, Set<UserRole> allowedRoles);
}
