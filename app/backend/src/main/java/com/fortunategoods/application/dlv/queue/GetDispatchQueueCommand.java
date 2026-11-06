package com.fortunategoods.application.dlv.queue;

import java.util.List;
import java.util.Set;

import com.fortunategoods.application.dlv.DispatchService;
import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.courier.DlvModulePermissions;
import com.fortunategoods.domain.user.UserRole;

public final class GetDispatchQueueCommand implements Command<List<DispatchQueueEntry>> {

    private final DispatchService service;
    private final Session session;

    public GetDispatchQueueCommand(DispatchService service, Session session) {
        this.service = service;
        this.session = session;
    }

    @Override
    public Set<UserRole> validRoles() {
        return DlvModulePermissions.viewDispatchQueueRoles();
    }

    @Override
    public CommandSuccess<List<DispatchQueueEntry>> execute() {
        List<DispatchQueueEntry> queue = service.dispatchQueue(session.getRole());
        return CommandSuccess.success(queue, queue.size() + " orders waiting for a courier");
    }

    @Override
    public boolean logsSuccess() {
        return false;
    }
}
