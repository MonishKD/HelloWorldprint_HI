package com.fortunategoods.application.ord;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter .HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;


@RestControllerAdvice(basePackages = "com.fortunategoods.application.ord")
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
}
