package com.fortunategoods.application.ord.execution;

import com.fasterxml.jackson.annotation.JsonProperty;

/*
    This class represents error/failure commands/errors, which is
    sent back to the frontend/controller.
*/
public final class CommandError {
    private final int status;
    private final String code;
    private final String msg;

    public CommandError(int status, String code, String msg){
        this.status = status;
        this.code = code;
        this.msg = msg;
    }

    public int getStatus(){
        return status;
    }

    public String getCode(){
        return code;
    }

    @JsonProperty("message")
    public String getMsg(){
        return msg;
    }
}