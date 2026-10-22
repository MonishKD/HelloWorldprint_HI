package com.fortunategoods.application.dashboard;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fortunategoods.application.ord.execution.CommandLog;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.technical.persistence.CommandLogRepository;

/*
    Builds the output console for the signed-in user (#140, R-DASH-06): their command attempts,
    successes, refusals and errors, oldest first.
    Only the user's own entries: another user's commands are never shown.
    @Service: Spring creates one of these and gives it to the classes that ask for it (here DashboardController).
*/
@Service
public class OutputConsoleFeed {

    // How many entries one page has. Older ones are loaded with "load more".
    public static final int PAGE_SIZE = 100;

    private final CommandLogRepository commandLogRepository;

    // Spring gives us the repository.
    public OutputConsoleFeed(CommandLogRepository commandLogRepository) {
        this.commandLogRepository = commandLogRepository;
    }

    // The newest 100 entries of the user, or with beforeId the 100 just older than that entry.
    // An empty list means there is nothing older to load.
    public List<ConsoleEntry> entriesFor(Session session, Long beforeId) {
        List<CommandLog> logs = commandLogRepository.findLatestForUser(session.getUserID(), PAGE_SIZE, beforeId);

        List<ConsoleEntry> entries = new ArrayList<>();
        for (CommandLog log : logs) {
            entries.add(toEntry(log));
        }
        return entries;
    }

    // Copies only the fields the console shows. The session (user id and role) is left out.
    private ConsoleEntry toEntry(CommandLog log) {
        return new ConsoleEntry(
                log.getId(),
                log.getCreatedAt(),
                log.getCommandName(),
                log.getResult().name(),
                log.getStatus(),
                log.getStatusCode(),
                log.getStatusMessage(),
                log.getEventId());
    }
}
