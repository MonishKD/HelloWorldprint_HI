package com.fortunategoods.application.ord;

import com.fortunategoods.application.ord.execution.Command;
import com.fortunategoods.application.ord.execution.CommandExecutionException;
import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandLog;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.ord.menu.BrowseMenuCommand;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.application.ord.menu.MenuService;
import com.fortunategoods.domain.restaurant.menu.RestaurantMenu;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.CommandLogRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.MenuItemRepository;
import com.fortunategoods.technical.persistence.UserRepository;
import com.fortunategoods.technical.security.SessionValidator;


/*
    This class is used to test the command invoker on various of Authentication/Unauthorized exceptions:
    - Customer can browse a menu
    - Users w/o accounts or unauthenticated users cannot browse menus
    - Couriers cannot browse menu
    - Restaurant staff cannot browse the menu of another restaurant
    - State transition with the wrong user role
    We compare the results sent after executing process() to the result obtained
    from the domain layer's appropriate method that is executed.

    This class also tests DomainRuleViolation Exceptions (400, 404, 409)

    This class also tests RuntimeExceptions (500)
*/
class CommandInvokerTest {

    private SessionValidator validator;
    private MenuService menuService;
    private CommandInvoker invoker;
    private List<CommandLog> savedLogs;

    @BeforeEach
    void setUp() {
        // Stand-ins for dependencies we are not testing here.
        validator = mock(SessionValidator.class);
        menuService = mock(MenuService.class);

        // Store logs in a list instead of a database.
        savedLogs = new ArrayList<>();

        invoker = new CommandInvoker(validator, new CommandLogRepository(null) { public void save(CommandLog log) { savedLogs.add(log); } });
    }

    @Test
    void customerCanBrowseMenu() {
        // Prepare a customer and a menu.
        Session customer = new Session(7, UserRole.CUSTOMER);

        RestaurantMenu menu = new RestaurantMenu("1", "Cafe", RestaurantStatus.OPEN, List.of());


        when(menuService.browseMenu("1", "7", UserRole.CUSTOMER)).thenReturn(menu);

        BrowseMenuCommand command = new BrowseMenuCommand(menuService, customer, "1");

        // Run the command.
        CommandSuccess<RestaurantMenu> result = invoker.process(command, customer);

        // Compare menu from MenuService to the menu you get when you call from CommandInvoker
        assertSame(menu, result.getData()); 
        assertEquals(1, savedLogs.size());
        assertEquals(CommandLog.Result.SUCCESS,savedLogs.get(0).getResult());

        // Verifications
        verify(validator).requireValid(customer);
        verify(validator).requireRoles(customer, Set.of(UserRole.CUSTOMER, UserRole.RESTAURANT));
        verify(menuService).browseMenu("1", "7", UserRole.CUSTOMER);
    }

    @Test
    void guestCannotBrowseMenu() {
        // Return null for null session
        doThrow(new AuthenticationCredentialsNotFoundException("Please sign in.")).when(validator).requireValid(null);

        BrowseMenuCommand command = new BrowseMenuCommand(menuService, null, "1");

        // An authentication exception should be thrown for null sessions
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> invoker.process(command, null));

        // The domain receiver was never reached.
        verifyNoInteractions(menuService);

        // Verify that UNAUTHORIZED status was sent
        assertEquals(1, savedLogs.size());
        assertEquals(401, savedLogs.get(0).getStatus());
        assertEquals(CommandLog.Result.REFUSED,savedLogs.get(0).getResult());

        CommandLog log = savedLogs.get(0);
        assertEquals("UNAUTHENTICATED", log.getStatusCode());
        assertEquals("Sign-in again to perform this action", log.getStatusMessage());
        assertNull(log.getEventId());
    }

    @Test
    void courierCannotBrowseMenu() {
        Session courier = new Session(9, UserRole.COURIER);

        Set<UserRole> allowedRoles = Set.of(UserRole.CUSTOMER, UserRole.RESTAURANT);

        // Validator refuses those roles
        doThrow(new AccessDeniedException("Wrong role.")).when(validator).requireRoles(courier, allowedRoles);

        BrowseMenuCommand command = new BrowseMenuCommand(menuService, courier, "1");

        assertThrows(AccessDeniedException.class, () -> invoker.process(command, courier));

        verifyNoInteractions(menuService);

        // Verify that FORBIDDEN status was sent
        assertEquals(1, savedLogs.size());
        assertEquals(403, savedLogs.get(0).getStatus());
        assertEquals(CommandLog.Result.REFUSED, savedLogs.get(0).getResult());

        CommandLog log = savedLogs.get(0);

        assertEquals("FORBIDDEN", log.getStatusCode());
        assertEquals("You are not allowed to perform this action",log.getStatusMessage());
        assertNull(log.getEventId());
    }

    @Test
    void staffCannotBrowseAnotherRestaurantsMenu() {
        Session staff = new Session(8, UserRole.RESTAURANT);

        UserRepository users = mock(UserRepository.class);
        RestaurantRepository restaurants = mock(RestaurantRepository.class);

        // Staff user 8 belongs to restaurant 1.
        when(users.findRestaurantIdByUserId("8")).thenReturn("1");

        MenuService realMenuService = new MenuService(restaurants, users, mock(MenuItemRepository.class));

        // Staff requests restaurant 2.
        BrowseMenuCommand command = new BrowseMenuCommand(realMenuService, staff, "2");

        // Try to process command with staff from restaurant 1 with browseMenuCommand from restaurant 2
        assertThrows(AccessDeniedException.class, () -> invoker.process(command, staff));

        // Ownership was refused before accessing the restaurant.
        verifyNoInteractions(restaurants);

        // Verify that FORBIDDEN status was sent
        assertEquals(1, savedLogs.size());
        assertEquals(403, savedLogs.get(0).getStatus());
        assertEquals(CommandLog.Result.REFUSED, savedLogs.get(0).getResult());

        CommandLog log = savedLogs.get(0);

        assertEquals("FORBIDDEN", log.getStatusCode());
        assertEquals("Restaurant staff can only view their own restaurant's menu", log.getStatusMessage());
        assertNull(log.getEventId());
    }

    @Test
    void wrongRoleCannotChangeState() {
        Session restaurantUser = new Session(8, UserRole.RESTAURANT);

        // This list represents state that the command would change.
        List<String> createdOrders = new ArrayList<>();

        // A test-only command that would add an order to the list.
        Command<String> createOrderCommand = new Command<String>() {

            @Override
            public Set<UserRole> validRoles() {
                return Set.of(UserRole.CUSTOMER);
            }

            @Override
            public CommandSuccess<String> execute() {
                createdOrders.add("Order 1");
                return CommandSuccess.success("Order 1", "Order created.");
            }
        };

        // A restaurant user cannot execute customer command.
        doThrow(new AccessDeniedException("Wrong role.")).when(validator).requireRoles(restaurantUser, Set.of(UserRole.CUSTOMER));
        assertThrows(AccessDeniedException.class,() -> invoker.process(createOrderCommand, restaurantUser));

  
        assertEquals(0, createdOrders.size());
        assertEquals(1, savedLogs.size());

        CommandLog log = savedLogs.get(0);
        assertEquals(CommandLog.Result.REFUSED, log.getResult());
        assertEquals(403, log.getStatus());

        assertEquals("FORBIDDEN", log.getStatusCode());
        assertFalse(log.getStatusMessage().isBlank());
        assertNull(log.getEventId());
    }
    
    @Test
    void rejectedSessionCannotExecute (){
        Session customer = new Session(20, UserRole.CUSTOMER);
        doThrow(new AuthenticationCredentialsNotFoundException("Session expired.")).when(validator).requireValid(customer);

        BrowseMenuCommand command = new BrowseMenuCommand(menuService, customer, "3");
        assertThrows(AuthenticationCredentialsNotFoundException.class, ()-> invoker.process(command, customer));

        verifyNoInteractions(menuService);
        assertEquals(1, savedLogs.size());

        CommandLog log = savedLogs.get(0);

        assertEquals(CommandLog.Result.REFUSED, log.getResult());
        assertEquals(401, log.getStatus());
        assertEquals("UNAUTHENTICATED", log.getStatusCode());
        assertNull(log.getSession());
        assertNull(log.getEventId());
    }

    // Domain failure tests ----------
    @Test 
    void invalidDomain400(){
        checkDomainFailure(DomainRuleViolation.Reason.INVALID, 400);
    }

    @Test 
    void notFoundDomain404(){
        checkDomainFailure(DomainRuleViolation.Reason.NOT_FOUND, 404);
    }

    @Test 
    void conflictDomain409(){
        checkDomainFailure(DomainRuleViolation.Reason.CONFLICT, 409);
    }

    private void checkDomainFailure(DomainRuleViolation.Reason reason, int expectedStatus) {
        Session customer = new Session(7, UserRole.CUSTOMER);

        // Will be thrown by one of the domain services
        DomainRuleViolation failure = new DomainRuleViolation(reason, "Action was refused.");

        // Make the service throw a domain rule violation
        when(menuService.browseMenu("1", "7", UserRole.CUSTOMER)).thenThrow(failure);

        BrowseMenuCommand command = new BrowseMenuCommand(menuService, customer, "1");

        CommandExecutionException e = assertThrows(CommandExecutionException.class,() -> invoker.process(command, customer));

        // Check what the invoker passed to the HTTP layer.
        assertEquals(expectedStatus, e.getStatus());
        assertEquals(reason.name(), e.getCode());
        assertEquals("Action was refused.", e.getMessage());
        assertSame(failure, e.getCause());

        // Check the saved refusal.
        assertEquals(1, savedLogs.size());

        CommandLog log = savedLogs.get(0);

        assertEquals(CommandLog.Result.REFUSED, log.getResult());
        assertEquals(expectedStatus, log.getStatus());
        assertEquals(reason.name(), log.getStatusCode());
        assertEquals("Action was refused.", log.getStatusMessage());
        assertNull(log.getEventId()); // No state transitions happened
    }

    @Test
    void unexpectedFailureProducesSafe500Error() {
        Session customer = new Session(7, UserRole.CUSTOMER);

        // Random RuntimeException to simulate
        RuntimeException failure = new IllegalStateException("Private database connection details");

        when(menuService.browseMenu("1", "7", UserRole.CUSTOMER)).thenThrow(failure);

        BrowseMenuCommand command = new BrowseMenuCommand(menuService, customer, "1");

        CommandExecutionException exception = assertThrows(CommandExecutionException.class, () -> invoker.process(command, customer));

        assertEquals(500, exception.getStatus());
        assertEquals("COMMAND_FAILED", exception.getCode());
        assertSame(failure, exception.getCause());

        assertFalse(exception.getMessage().contains("Private database connection details"));

        assertEquals(1, savedLogs.size());

        CommandLog log = savedLogs.get(0);
        assertEquals(CommandLog.Result.ERROR, log.getResult());
        assertEquals(500, log.getStatus());
        assertEquals("COMMAND_FAILED", log.getStatusCode());

        assertFalse(log.getStatusMessage().contains("Private database connection details"));

        assertNull(log.getEventId());
    }
}
