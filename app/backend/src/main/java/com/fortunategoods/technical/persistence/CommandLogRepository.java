package com.fortunategoods.technical.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fortunategoods.application.ord.execution.CommandLog;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.user.UserRole;

// Saves and reads command log entries in the database (#142, R-DASH-06).
// CommandInvoker (#68) saves every command attempt here, refusals and errors included. The console feed (#140) reads them back.
// One row in command_log is one CommandLog, with the same fields.
@Repository
public class CommandLogRepository {

    // The table keeps this many entries at most. Older ones are deleted.
    private static final int MAX_ENTRIES = 1000;

    private final JdbcTemplate jdbc;

    public CommandLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Saves the entry, then deletes everything older than the latest 1,000.
    // @Transactional means: the insert and the delete happen together, or neither does.
    @Transactional
    public void save(CommandLog log) {
        // No session means the user was not signed in (401), so we don't know who it was.
        Long userId = null;
        String role = null;
        if (log.getSession() != null) {
            userId = log.getSession().getUserID();
            role = log.getSession().getRole().name();
        }

        String sql = "INSERT INTO command_log (command_name, user_id, role, result, status, status_code, status_message, event_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        jdbc.update(sql, log.getCommandName(), userId, role, log.getResult().name(), log.getStatus(),
                log.getStatusCode(), log.getStatusMessage(), log.getEventId());

        // Keeps the 1,000 newest rows (the highest ids) and deletes the rest.
        String trimSql = "DELETE FROM command_log WHERE id NOT IN (SELECT id FROM command_log ORDER BY id DESC LIMIT ?)";
        jdbc.update(trimSql, MAX_ENTRIES);
    }

    // Returns the latest entries (at most count of them), oldest first so they read in time order.
    // The inner SELECT takes the newest ones, the outer one puts them back oldest first.
    // Sorted by id and not created_at: ids go up with every save and never tie, two saves can have the same time.
    public List<CommandLog> findLatest(int count) {
        String sql = "SELECT * FROM (SELECT * FROM command_log ORDER BY id DESC LIMIT ?) AS latest ORDER BY id";
        return jdbc.query(sql, (row, rowNumber) -> toCommandLog(row), count);
    }

    // Turns one row of the command_log table into a CommandLog.
    // user_id and event_id can be empty, so we check first. getLong would give 0 instead of null.
    private CommandLog toCommandLog(ResultSet row) throws SQLException {
        Session session = null;
        if (row.getObject("user_id") != null) {
            session = new Session(row.getLong("user_id"), UserRole.valueOf(row.getString("role")));
        }

        Long eventId = null;
        if (row.getObject("event_id") != null) {
            eventId = row.getLong("event_id");
        }

        return new CommandLog(
                row.getString("command_name"),
                session,
                CommandLog.Result.valueOf(row.getString("result")),
                row.getInt("status"),
                row.getString("status_code"),
                row.getString("status_message"),
                eventId);
    }
}
