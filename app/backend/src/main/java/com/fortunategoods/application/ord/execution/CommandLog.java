package com.fortunategoods.application.ord.execution;
import java.time.LocalDateTime;

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
    private final Long id;                  // database id, null until the entry is saved
    private final LocalDateTime createdAt;  // when it was saved, null until the entry is saved

    // Used by CommandInvoker for a new entry that is not saved yet.
    public CommandLog(String commandName, Session session, Result result, int status, String statusCode, String statusMessage, Long eventId){
        this(null, null, commandName, session, result, status, statusCode, statusMessage, eventId);
    }

    // Used by CommandLogRepository when it reads a saved entry back, with its id and time (#140).
    public CommandLog(Long id, LocalDateTime createdAt, String commandName, Session session, Result result, int status, String statusCode, String statusMessage, Long eventId){
            this.id = id;
            this.createdAt = createdAt;
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

    public Long getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
