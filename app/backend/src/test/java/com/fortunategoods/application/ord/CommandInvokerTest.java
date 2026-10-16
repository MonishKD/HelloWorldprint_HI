package com.fortunategoods.application.ord;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.restaurant.MenuService;
import com.fortunategoods.domain.restaurant.RestaurantMenu;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;
import com.fortunategoods.technical.security.SessionValidator;

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

        invoker = new CommandInvoker(validator, log -> savedLogs.add(log));
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

        MenuService realMenuService = new MenuService(restaurants, users);

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
}