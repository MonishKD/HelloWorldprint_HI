package com.fortunategoods.technical.persistence;
import com.fortunategoods.application.ord.CommandLog;

public interface CommandLogRepository {
    void save(CommandLog log);
}
