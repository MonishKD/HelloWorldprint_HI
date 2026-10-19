package com.fortunategoods.application.ord;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortunategoods.application.ord.restaurants.RestaurantService;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;

class RestaurantStatusServiceTest {

    private final RestaurantRepository restaurants = mock(RestaurantRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RestaurantService service = new RestaurantService(restaurants, users);
    private Restaurant restaurant;

    @BeforeEach
    void setup() {
        MenuItem item = new MenuItem("10", "1", "Wrap", MenuItemStatus.AVAILABLE, 4.50);
        restaurant = new Restaurant("1", "Cafe", RestaurantStatus.CLOSED, 45.5, -73.6, List.of(item));

        when(users.findRestaurantIdByUserId("8")).thenReturn("1");
        when(restaurants.findById("1")).thenReturn(restaurant);
    }

    @Test
    void staffCanOpenPauseAndClose() {
        for (RestaurantStatus status : RestaurantStatus.values()) {
            service.setRestaurantStatus("8", UserRole.RESTAURANT, "1", status);

            assertEquals(status, restaurant.getStatus());
            verify(restaurants).updateStatus("1", status);
        }
    }

    @Test
    void openingEmptyMenuIsRefused() {
        restaurant.setMenu(List.of());

        refused(UserRole.RESTAURANT, DomainRuleViolation.Reason.CONFLICT);
    }

    @Test
    void anotherRestaurantIsRefused() {
        when(users.findRestaurantIdByUserId("8")).thenReturn("2");

        refused(UserRole.RESTAURANT, DomainRuleViolation.Reason.FORBIDDEN);
    }

    @Test
    void customerIsRefused() {
        refused(UserRole.CUSTOMER, DomainRuleViolation.Reason.FORBIDDEN);
    }

    private void refused(UserRole role, DomainRuleViolation.Reason reason) {
        DomainRuleViolation error = assertThrows(DomainRuleViolation.class,() -> service.setRestaurantStatus("8", role, "1", RestaurantStatus.OPEN));

        assertEquals(reason, error.getReason());
        assertEquals(RestaurantStatus.CLOSED, restaurant.getStatus());
        verify(restaurants, never()).updateStatus(anyString(), any(RestaurantStatus.class));
    }
}
