package com.fortunategoods.application.prc.history;

import java.util.List;
import java.util.Set;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.prc.LedgerService;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.user.UserRole;

/*
    Command for any signed-in user to read their own history (#264, R-PRC-04). LedgerService is the receiver.
    All four roles are valid. Which rows they get comes from the session, never from the request.
    Run it through CommandInvoker.process(command, session) so the session and role are checked first.
*/
public final class GetHistoryCommand implements Command<List<HistoryResponse>> {

    private final LedgerService service;
    private final Session session;

    public GetHistoryCommand(LedgerService service, Session session) {
        this.service = service;
        this.session = session;
    }

    @Override
    public Set<UserRole> validRoles() {
        return OrdModulePermissions.viewHistoryRoles();
    }

    @Override
    public CommandSuccess<List<HistoryResponse>> execute() {
        List<HistoryResponse> rows = service.historyOf(session);
        return CommandSuccess.success(rows, rows.size() + " history rows loaded");
    }
}
