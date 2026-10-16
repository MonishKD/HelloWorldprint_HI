package com.fortunategoods.technical.security;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.user.UserRole;
import java.util.Set;

/*
    Class that commandInvoker will call in the Technical Services layer to verify
    authentication and permissions
*/
public interface SessionValidator {

    // Checks session against Spring session, if it fails throws AuthenticationException
    void requireValid(Session session);

    // Checks session's roles with allowedRoles from command attempted at being
    // executed. If it fails, throws AccessDeniedException.
    void requireRoles(Session session, Set<UserRole> allowedRoles);
}
