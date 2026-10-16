package com.fortunategoods.application.ord;
import com.fortunategoods.application.reg.Session;

public class CommandLog {
    public enum Result{
        SUCCESS,
        REFUSED,
        ERROR
    }

    private final String commandName;
    private final Session session;
    private final Result result;
    private final int status; // 200
    private final String statusCode; // OK
    private final String statusMessage; // Command's outcome i.e. order accepted
    private final Long eventId;

    public CommandLog(String commandName, Session session, Result result, int status, String statusCode, String statusMessage, Long eventId){
            this.commandName = commandName;
            this.session = session;
            this.result = result;
            this.status = status;
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.eventId = eventId;
    }

    public String getCommandName() {
        return commandName;
    }

    public Session getSession() {
        return session;
    }

    public Result getResult() {
        return result;
    }

    public int getStatus() {
        return status;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public Long getEventId() {
        return eventId;
    }
}
