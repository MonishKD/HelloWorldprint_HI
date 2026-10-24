package com.fortunategoods.application.ord;
import com.fortunategoods.application.ord.execution.CommandError;
import com.fortunategoods.application.ord.execution.CommandExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter .HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.fortunategoods.domain.order.state.IllegalTransitionException;


@RestControllerAdvice(basePackages = {"com.fortunategoods.application.ord", "com.fortunategoods.application.dashboard"})
public final class OrderExceptionHandler {
    /*
        Method that will turn all CommandExecutionException into an HTTP response
    */
    @ExceptionHandler(CommandExecutionException.class)
    public ResponseEntity<CommandError> handleException(CommandExecutionException e){
        CommandError err = new CommandError(e.getStatus(), e.getCode(), e.getMessage());
        return ResponseEntity.status(e.getStatus()).body(err);
    }


    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class
    })
    public ResponseEntity<CommandError> handleBadReq(Exception e){
        CommandError err = new CommandError(400, "INVALID", "Request is invalid");
        return ResponseEntity.badRequest().body(err);
    }


    @ExceptionHandler(IllegalTransitionException.class)
     public ResponseEntity<CommandError> handlerBadTransition(Exception e){
        CommandError err = new CommandError(409, "CONFLICT", "Action not allowed due to order's state");
        //https://stackoverflow.com/questions/63418088/how-to-get-a-409-for-objectoptimisticlockingfailureexception-with-spring-boot
        return ResponseEntity.status(HttpStatus.CONFLICT).body(err);
    }
    
}
