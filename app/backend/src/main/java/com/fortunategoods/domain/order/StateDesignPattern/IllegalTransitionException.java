package com.fortunategoods.domain.order.StateDesignPattern;
//standard implementation of new exception
public class IllegalTransitionException extends RuntimeException  {
    public IllegalTransitionException(String reason){
        super(reason);
    }
}
