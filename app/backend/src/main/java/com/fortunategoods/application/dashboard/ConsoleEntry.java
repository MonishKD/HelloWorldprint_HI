package com.fortunategoods.application.dashboard;

import java.time.LocalDateTime;

/*
    One line of the output console, as the frontend receives it (CommandLogRow in dashboardApi.ts).
    Spring turns the getters into JSON fields: getCommandName() becomes "commandName", and so on.
    It has no user id or role on purpose: the console never shows who else did what.
*/
public class ConsoleEntry {

    private final long id;
    private final LocalDateTime createdAt;
    private final String commandName;
    private final String result;        // SUCCESS, REFUSED or ERROR
    private final int status;           // 200, 403, 409...
    private final String statusCode;    // OK, FORBIDDEN...
    private final String statusMessage; // what happened, e.g. "Order accepted"
    private final Long eventId;         // order event made by the command, null if none

    public ConsoleEntry(long id, LocalDateTime createdAt, String commandName, String result, int status,String statusCode, String statusMessage, Long eventId) {
        this.id = id;
        this.createdAt = createdAt;
        this.commandName = commandName;
        this.result = result;
        this.status = status;
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.eventId = eventId;
    }

    public long getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getCommandName() {
        return commandName;
    }

    public String getResult() {
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
