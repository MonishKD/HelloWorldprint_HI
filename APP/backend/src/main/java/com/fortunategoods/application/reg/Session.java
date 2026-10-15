package com.fortunategoods.application.reg;
import com.fortunategoods.domain.user.UserRole;
import java.util.Objects;

/*
    Class that carries the signed-in user's id and role as a User. No 
    authentication of the user present here.
*/
public final class Session{
    private final long userId;
    private final UserRole role;

    public Session(long userId, UserRole role){
        this.userId = userId;
        this.role = role;
    }

    public long getUserID(){
        return userId;
    }

    public UserRole getRole(){
        return role;
    }
    
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof Session)) {
            return false;
        }

        Session session = (Session) other;

        return userId == session.userId && role == session.role;
    }

    @Override
    public int hashCode(){
        return Objects.hash(userId, role);
    }
}
