package com.fortunategoods.application.prc.summary;

import java.util.Set;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.prc.LedgerService;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.user.UserRole;

/*
    Command for reading a delivered order's summary (#262). LedgerService is the receiver.
    Valid roles are CUSTOMER (their own orders only) and OPERATOR (any order).
    Run it through CommandInvoker.process(command, session) so the session and role are checked first.
*/
public final class GetOrderSummaryCommand implements Command<OrderSummaryResponse> {

    private final LedgerService service;
    private final Session session;
    private final String orderId;

    public GetOrderSummaryCommand(LedgerService service, Session session, String orderId) {
        this.service = service;
        this.session = session;
        this.orderId = orderId;
    }

    @Override
    public Set<UserRole> validRoles() {
        return OrdModulePermissions.viewOrderSummaryRoles();
    }

    @Override
    public CommandSuccess<OrderSummaryResponse> execute() {
        OrderSummaryResponse summary = service.summaryOf(session, orderId);
        return CommandSuccess.success(summary, "Summary of order " + orderId + " loaded: total $" + summary.getTotal());
    }
}
