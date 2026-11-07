package com.fortunategoods.application.ord.execution;
import java.util.Set;
import com.fortunategoods.domain.user.UserRole;
/*
    Interface for concrete commands
    T represents the type of data it should return
*/
public interface Command<T> {
    Set<UserRole> validRoles();
    CommandSuccess<T> execute();

    // Reads that dashboards poll every few seconds turn this off, or they would push real commands out of the console.
    default boolean logsSuccess() {
        return true;
    }
}
