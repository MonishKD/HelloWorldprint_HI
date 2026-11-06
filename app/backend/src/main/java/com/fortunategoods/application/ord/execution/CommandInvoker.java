package com.fortunategoods.application.ord.execution;
// SpringBoot imports
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
// FortunateGoods imports
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.technical.security.SessionValidator;
import com.fortunategoods.technical.persistence.CommandLogRepository;

/*
    Class that will run a process method, which will take care of
    checking user's session validity before executing any commands
*/
@Service
public final class CommandInvoker {
    private final SessionValidator sessionValidator;
    private final CommandLogRepository commandLogRepo;

    public CommandInvoker(SessionValidator sessionValidator, CommandLogRepository commandLogRepo){
        this.sessionValidator = sessionValidator;
        this.commandLogRepo = commandLogRepo;
    }

    /*
        process takes the concrete command executed + user session
    */
    public <T> CommandSuccess<T> process(Command<T> command, Session userSession){
        String commandName = command.getClass().getSimpleName(); // used in the CommandLog entries
        
        Session validSession = null;
        CommandSuccess<T> result;

        try{
            // 1. Validate user session
            sessionValidator.requireValid(userSession);
            // 2. Assign it to the null variable, if successful
            validSession = userSession;

            // 3. Check if user role is allowed to execute command
            sessionValidator.requireRoles(validSession, command.validRoles());

            // 4. Execute command
            result = command.execute();
            if (result == null){
                throw new IllegalStateException("Command returned no results");
            }
        } catch (AuthenticationException e){
            CommandLog authLog = new CommandLog(commandName, null, CommandLog.Result.REFUSED, 401, "UNAUTHENTICATED", "Sign-in again to perform this action", null);
            commandLogRepo.save(authLog);
            throw e;

        } catch (AccessDeniedException e){
            CommandLog accessLog = new CommandLog(commandName, validSession, CommandLog.Result.REFUSED, 403, "FORBIDDEN", "You are not allowed to perform this action", null);
            commandLogRepo.save(accessLog);
            throw e;
            
        } catch (DomainRuleViolation e){
            int status = switch(e.getReason()){
                case INVALID -> 400;
                case NOT_FOUND -> 404;
                case CONFLICT -> 409;
                case FORBIDDEN -> 403;
            };
            String code = e.getReason().name();
            CommandLog domainLog = new CommandLog(commandName, validSession, CommandLog.Result.REFUSED, status, code, e.getMessage(), null);
            commandLogRepo.save(domainLog);

            // Check if at the time the command is executed, user is stil allowed
            if (e.getReason() == DomainRuleViolation.Reason.FORBIDDEN) {

                throw new AccessDeniedException(e.getMessage(), e);
            }

            throw new CommandExecutionException(status, code, e.getMessage(), e);
            

        } catch (RuntimeException e){
            CommandLog runtimeLog = new CommandLog(commandName, validSession, CommandLog.Result.ERROR, 500, "COMMAND_FAILED", "The action failed and could not be completed", null);
            commandLogRepo.save(runtimeLog);

            throw new CommandExecutionException(500, "COMMAND_FAILED", "The action failed and could not be completed.", e);
        }

        if (command.logsSuccess()) {
            CommandLog successLog = new CommandLog(commandName, validSession, CommandLog.Result.SUCCESS, 200, "OK", result.getMsg(), result.getEventId());
            commandLogRepo.save(successLog);
        }
        return result;
    }

    //private void record(String commandName, Session session, CommandLog.Result result, int status, String statusCode, String statusMessage, long eventId)
    
}
