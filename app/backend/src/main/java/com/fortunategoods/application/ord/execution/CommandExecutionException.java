package com.fortunategoods.application.ord.execution;

/*
    This class extends a RuntimeException class, so that an error can be thrown
    in CommandInvoker, if it ever fails.
*/
public final class CommandExecutionException extends RuntimeException {

    private final int status;
    private final String statusCode;

    public CommandExecutionException(int status, String statusCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.statusCode = statusCode;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return statusCode;
    }
}
