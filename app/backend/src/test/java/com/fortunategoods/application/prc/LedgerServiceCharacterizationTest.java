package com.fortunategoods.application.prc;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fortunategoods.application.prc.history.CourierHistoryResponse;
import com.fortunategoods.application.prc.history.HistoryResponse;
import com.fortunategoods.application.prc.history.LedgerHistoryResponse;
import com.fortunategoods.application.prc.summary.OrderSummaryLineResponse;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.pricing.FareCalculator;
import com.fortunategoods.domain.pricing.LedgerEntry;
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.*;

// R2 characterization: protect historyOf(), summaryOf(), and the mapping/name lookups being extracted.
class LedgerServiceCharacterizationTest {
    private static final LocalDateTime PLACED = LocalDateTime.of(2026, 11, 5, 12, 0);
    private static final LocalDateTime DELIVERED = PLACED.plusHours(1);
    private final LedgerRepository ledger = mock(LedgerRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final RestaurantRepository restaurants = mock(RestaurantRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final LedgerService service = new LedgerService(ledger, orders, mock(OrderEventRepository.class),
            mock(FareCalculator.class), restaurants, users);
    private final LedgerEntry charge = new LedgerEntry("100", LedgerEntry.Type.CHARGE, OrderStatus.DELIVERED,
            6027, 299, 603, 200, 7129, PLACED, DELIVERED, "pi_1");

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"CUSTOMER", "RESTAURANT", "OPERATOR"})
    void historyUsesTheSessionScopeAndKeepsRepositoryOrder(UserRole role) {
        var refund = LedgerEntry.refund(charge, DELIVERED.plusHours(1));
        var cancelled = LedgerEntry.noCharge("101", OrderStatus.CANCELLED, PLACED, DELIVERED);
        var entries = List.of(refund, cancelled, charge);
        switch (role) {
            case CUSTOMER -> when(ledger.findByCustomer("7")).thenReturn(entries);
            case RESTAURANT -> {
                when(users.findRestaurantIdByUserId("7")).thenReturn("1");
                when(ledger.findByRestaurant("1")).thenReturn(entries);
            }
            case OPERATOR -> when(ledger.findAll()).thenReturn(entries);
            default -> throw new AssertionError("Unexpected test role");
        }
        when(orders.findById("100")).thenReturn(order("100", OrderStatus.REFUNDED));
        when(orders.findById("101")).thenReturn(order("101", OrderStatus.CANCELLED));
        when(restaurants.findById("1")).thenReturn(restaurant());

        var rows = service.historyOf(new Session(7, role));

        assertThat(rows).extracting(HistoryResponse::getOrderId).containsExactly("100", "101", "100");
        assertThat(rows).allSatisfy(row -> assertEquals("Cafe", row.getRestaurantName()));
        assertLedgerRow(rows.get(0), "REFUND", "REFUNDED", -6027, -299, -603, -200, -7129, DELIVERED.plusHours(1));
        assertLedgerRow(rows.get(1), "NO_CHARGE", "CANCELLED", 0, 0, 0, 0, 0, DELIVERED);
        assertLedgerRow(rows.get(2), "CHARGE", "DELIVERED", 6027, 299, 603, 200, 7129, DELIVERED);
        verify(restaurants).findById("1"); // One name lookup for the shared restaurant.
    }

    @Test
    void restaurantHistoryRefusesAnUnlinkedAccount() {
        var error = assertThrows(DomainRuleViolation.class,
                () -> service.historyOf(new Session(8, UserRole.RESTAURANT)));

        assertViolation(error, DomainRuleViolation.Reason.FORBIDDEN, "Your account is not linked to a restaurant");
        verifyNoInteractions(ledger, orders, restaurants);
    }

    @Test
    void courierHistorySkipsRefundsAndNoChargeRowsAndHasNoMoneyFields() {
        when(ledger.findByCourier("15")).thenReturn(List.of(
                LedgerEntry.refund(charge, DELIVERED.plusHours(1)), charge,
                LedgerEntry.noCharge("101", OrderStatus.REJECTED, PLACED, DELIVERED)));
        when(orders.findById("100")).thenReturn(order("100", OrderStatus.REFUNDED));
        when(restaurants.findById("1")).thenReturn(restaurant());
        when(users.findCustomerName("7")).thenReturn("Alice");

        var rows = service.historyOf(new Session(15, UserRole.COURIER));

        assertEquals(1, rows.size());
        var row = assertInstanceOf(CourierHistoryResponse.class, rows.get(0));
        assertEquals("100", row.getOrderId());
        assertEquals("Cafe", row.getRestaurantName());
        assertEquals("Alice", row.getDestinationName());
        assertEquals(DELIVERED, row.getDeliveredAt());
        assertThat(new JsonMapper().valueToTree(row).properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("orderId", "restaurantName", "destinationName", "deliveredAt");
    }

    @Test
    void historyKeepsRowsWhenTheRestaurantWasDeletedAndCachesTheMissingName() {
        when(ledger.findByCustomer("7")).thenReturn(List.of(charge, LedgerEntry.refund(charge, DELIVERED.plusHours(1))));
        when(orders.findById("100")).thenReturn(order("100", OrderStatus.REFUNDED));

        var rows = service.historyOf(new Session(7, UserRole.CUSTOMER));

        assertThat(rows).hasSize(2).allSatisfy(row -> assertNull(row.getRestaurantName()));
        verify(restaurants).findById("1");
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void historyWithoutEntriesIsEmpty(UserRole role) {
        when(users.findRestaurantIdByUserId("7")).thenReturn("1");

        assertTrue(service.historyOf(new Session(7, role)).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"CUSTOMER, 7, DELIVERED", "CUSTOMER, 7, REFUNDED",
            "OPERATOR, 9, DELIVERED", "OPERATOR, 9, REFUNDED"})
    void summaryKeepsTheOriginalChargeAndOrderDetails(UserRole role, long userId, OrderStatus status) {
        when(orders.findById("100")).thenReturn(order("100", status));
        when(ledger.findCharge("100")).thenReturn(charge);
        when(restaurants.findById("1")).thenReturn(restaurant());
        when(users.findDeliveringCourierName("100")).thenReturn("Carl");
        when(orders.findCardBrand("100")).thenReturn("visa");
        when(orders.findCardLast4("100")).thenReturn("4242");

        var summary = service.summaryOf(new Session(userId, role), "100");

        assertEquals("100", summary.getOrderId());
        assertThat(summary.getLines()).extracting(OrderSummaryLineResponse::getName,
                OrderSummaryLineResponse::getQuantity, OrderSummaryLineResponse::getPriceCents)
                .containsExactly(tuple("Meal", 2, 2999L), tuple("Tea", 1, 29L));
        assertEquals(6027, summary.getSubtotalCents());
        assertEquals(299, summary.getDeliveryFeeCents());
        assertEquals(603, summary.getServiceFeeCents());
        assertEquals(200, summary.getSurchargeCents());
        assertEquals(7129, summary.getTotalCents());
        assertEquals(PLACED, summary.getPlacedAt());
        assertEquals(DELIVERED, summary.getDeliveredAt());
        assertEquals("Cafe", summary.getRestaurantName());
        assertEquals("Carl", summary.getCourierName());
        assertEquals("123 Main St", summary.getDeliveryAddress());
        assertEquals("visa", summary.getCardBrand());
        assertEquals("4242", summary.getCardLast4());
    }

    @Test
    void summaryAllowsMissingRestaurantCourierAndCardDetails() {
        when(orders.findById("100")).thenReturn(order("100", OrderStatus.DELIVERED));
        when(ledger.findCharge("100")).thenReturn(charge);

        var summary = service.summaryOf(new Session(7, UserRole.CUSTOMER), "100");

        assertNull(summary.getRestaurantName());
        assertNull(summary.getCourierName());
        assertNull(summary.getCardBrand());
        assertNull(summary.getCardLast4());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "abc", "0", "-1"})
    void summaryValidatesTheIdBeforeReadingAnOrder(String id) {
        var error = assertThrows(DomainRuleViolation.class,
                () -> service.summaryOf(new Session(7, UserRole.CUSTOMER), id));

        assertViolation(error, DomainRuleViolation.Reason.INVALID, "Order ID must be a positive integer");
        verifyNoInteractions(orders, ledger, restaurants, users);
    }

    @Test
    void summaryReportsAnUnknownOrderBeforeLookingForACharge() {
        var error = assertThrows(DomainRuleViolation.class,
                () -> service.summaryOf(new Session(7, UserRole.CUSTOMER), "100"));

        assertViolation(error, DomainRuleViolation.Reason.NOT_FOUND, "Order not found");
        verifyNoInteractions(ledger, restaurants, users);
    }

    @Test
    void summaryRefusesAnotherCustomersOrderBeforeLookingForACharge() {
        when(orders.findById("100")).thenReturn(order("100", OrderStatus.PLACED));

        var error = assertThrows(DomainRuleViolation.class,
                () -> service.summaryOf(new Session(8, UserRole.CUSTOMER), "100"));

        assertViolation(error, DomainRuleViolation.Reason.FORBIDDEN, "You can only see the summary of your own orders");
        verifyNoInteractions(ledger, restaurants, users);
    }

    @Test
    void summaryRequiresACharge() {
        when(orders.findById("100")).thenReturn(order("100", OrderStatus.PLACED));

        var error = assertThrows(DomainRuleViolation.class,
                () -> service.summaryOf(new Session(7, UserRole.CUSTOMER), "100"));

        assertViolation(error, DomainRuleViolation.Reason.CONFLICT,
                "The order summary is only available once the order is delivered");
        verifyNoInteractions(restaurants, users);
    }

    private static Order order(String id, OrderStatus status) {
        return new Order(id, "7", "1", "15", status, null, "123 Main St", null, null, null, List.of(
                OrderLine.fromMenuItem(new MenuItem("10", "1", "Meal", MenuItemStatus.AVAILABLE, 29.99), 2),
                OrderLine.fromMenuItem(new MenuItem("11", "1", "Tea", MenuItemStatus.AVAILABLE, 0.29), 1)));
    }

    private static Restaurant restaurant() {
        return new Restaurant("1", "Cafe", RestaurantStatus.OPEN, 45.5, -73.6, List.of());
    }

    private static void assertLedgerRow(HistoryResponse response, String type, String status,
            long subtotal, long delivery, long serviceFee, long surcharge, long amount, LocalDateTime finished) {
        var row = assertInstanceOf(LedgerHistoryResponse.class, response);
        assertEquals(type, row.getEntryType());
        assertEquals(status, row.getFinalStatus());
        assertEquals(subtotal, row.getSubtotalCents());
        assertEquals(delivery, row.getDeliveryFeeCents());
        assertEquals(serviceFee, row.getServiceFeeCents());
        assertEquals(surcharge, row.getSurchargeCents());
        assertEquals(amount, row.getAmountCents());
        assertEquals(PLACED, row.getPlacedAt());
        assertEquals(finished, row.getFinishedAt());
    }

    private static void assertViolation(DomainRuleViolation error, DomainRuleViolation.Reason reason, String message) {
        assertEquals(reason, error.getReason());
        assertEquals(message, error.getMessage());
    }
}
