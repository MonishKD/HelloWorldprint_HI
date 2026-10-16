package com.fortunategoods.domain.order;

/*
    Class that returns exception in the domain layer (basically business 
    rule exception). I.e. OrderService can throw this, if anything does not
    go as expected.
*/
public class DomainRuleViolation extends RuntimeException {
    public enum Reason{
        INVALID,
        NOT_FOUND,
        CONFLICT,
        FORBIDDEN
    }

    private final Reason reason;

    public DomainRuleViolation(Reason reason, String msg){
        super(msg);
        this.reason = reason;
    }

    public Reason getReason(){
        return reason;
    }
}
