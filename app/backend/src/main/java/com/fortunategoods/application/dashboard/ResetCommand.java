package com.fortunategoods.application.dashboard;

import java.util.Set;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.configuration.SeedLoader;

/*
    Command for the operator's Reset (#276, PH3 5.4 DASH): wipes every table and seeds again from seed-config.json.
    SeedLoader is the receiver. Run it through CommandInvoker.process(command, session) so the session and role are checked first.

    The console line survives the wipe: CommandInvoker saves it in command_log only after execute() returns,
    so it is written after the wipe, into the new empty table. The operator stays signed in, because sign-in lives
    in the Spring session (not in our tables) and the seeded operator always gets the same id back.
*/
public final class ResetCommand implements Command<Void> {

    private final SeedLoader seedLoader;

    public ResetCommand(SeedLoader seedLoader) {
        this.seedLoader = seedLoader;
    }

    @Override
    public Set<UserRole> validRoles() {
        return OrdModulePermissions.resetRoles();
    }

    @Override
    public CommandSuccess<Void> execute() {
        seedLoader.resetToSeed();
        return CommandSuccess.success(null, "System reset to its initial state");
    }
}
