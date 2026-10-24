package com.fortunategoods.domain.restaurant;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;
import static com.fortunategoods.testsupport.TestData.wrap;

// RestaurantService: the restaurant list, and opening, pausing or closing a restaurant.
// The repositories are mocked: restaurant "1" Cafe is closed with one menu item, staff user 8 works there.
class RestaurantServiceTest {

    private final RestaurantRepository restaurants = mock(RestaurantRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RestaurantService service = new RestaurantService(restaurants, users);
    private Restaurant cafe;

    @BeforeEach
    void setup() {
        cafe = new Restaurant("1", "Cafe", RestaurantStatus.CLOSED, 45.5, -73.6, List.of(wrap()));
        when(users.findRestaurantIdByUserId("8")).thenReturn("1");
        when(restaurants.findById("1")).thenReturn(cafe);
    }

    @Test
    void listReturnsEveryRestaurantWithStatusAndCoordinates() {
        when(restaurants.findAll()).thenReturn(List.of(
                new Restaurant("1", "Cafe", RestaurantStatus.OPEN, 45.5, -73.6, List.of()),
                new Restaurant("2", "Deli", RestaurantStatus.PAUSED, 45.4, -73.5, List.of()),
                new Restaurant("3", "Bakery", RestaurantStatus.CLOSED, 45.3, -73.4, List.of())));

        List<RestaurantSummary> list = service.browseRestaurants("7", UserRole.CUSTOMER);

        assertEquals(3, list.size());
        assertEquals(new RestaurantSummary("1", "Cafe", RestaurantStatus.OPEN, 45.5, -73.6), list.get(0));
        assertEquals(RestaurantStatus.PAUSED, list.get(1).status());
        assertEquals(RestaurantStatus.CLOSED, list.get(2).status());
    }

    @Test
    void noRestaurantsGivesAnEmptyList() {
        assertTrue(service.browseRestaurants("7", UserRole.CUSTOMER).isEmpty());
    }

    @Test
    void staffCanOpenPauseAndClose() {
        for (RestaurantStatus status : RestaurantStatus.values()) {
            service.setRestaurantStatus("8", UserRole.RESTAURANT, "1", status);
            assertEquals(status, cafe.getStatus());
            verify(restaurants).updateStatus("1", status);
        }
    }

    @Test
    void statusChangeIsRefusedForEmptyMenuOtherRestaurantsAndCustomers() {
        cafe.setMenu(List.of());
        assertStatusRefused(UserRole.RESTAURANT, DomainRuleViolation.Reason.CONFLICT);

        when(users.findRestaurantIdByUserId("8")).thenReturn("2");
        assertStatusRefused(UserRole.RESTAURANT, DomainRuleViolation.Reason.FORBIDDEN);

        assertStatusRefused(UserRole.CUSTOMER, DomainRuleViolation.Reason.FORBIDDEN);
        verify(restaurants, never()).updateStatus(anyString(), any(RestaurantStatus.class));
    }

    private void assertStatusRefused(UserRole role, DomainRuleViolation.Reason reason) {
        DomainRuleViolation error = assertThrows(DomainRuleViolation.class,
                () -> service.setRestaurantStatus("8", role, "1", RestaurantStatus.OPEN));
        assertEquals(reason, error.getReason());
        assertEquals(RestaurantStatus.CLOSED, cafe.getStatus());
    }
}
