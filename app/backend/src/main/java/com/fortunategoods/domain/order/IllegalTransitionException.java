package com.fortunategoods.domain.order;
//standard implementation of new exception
public class IllegalTransitionException extends RuntimeException  {
    public IllegalTransitionException(String reason){
        super(reason);
    }
}
