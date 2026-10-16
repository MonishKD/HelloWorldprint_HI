package com.fortunategoods.application.ord;

/*
    This class is used for returning successful actions that were executed
    back to the frontend/controller
*/
public final class CommandSuccess<T>{
    private final T data;
    private final String msg;
    private final Long eventId;

    public CommandSuccess(T data, String msg, Long eventId){
        this.data = data;
        this.msg = msg;
        this.eventId = eventId;
    }

    public T getData(){
        return data;
    }

    public String getMsg(){
        return msg;
    }

    public Long getEventId(){
        return eventId;
    }

    public static <T> CommandSuccess<T> success(T data, String msg) {
        return new CommandSuccess<>(data, msg, null);
    }
}
