package com.fortunategoods.domain.order;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fortunategoods.domain.order.state.IllegalTransitionException;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.domain.restaurant.MenuItemStatus;


public class OrderStateTest{

    Order testOrder;

    @BeforeEach 
    void init(){
    MenuItem item = new MenuItem("123","456","CheeseDog",MenuItemStatus.AVAILABLE,123.0);
    List<MenuItem> menu = new ArrayList<>();
    menu.add(item);
    OrderLine line = OrderLine.fromMenuItem(menu.get(0), 1);
    List<OrderLine> orderLines = new ArrayList<>();
    orderLines.add(line);
    testOrder = Order.placed("123","456","789",orderLines);
    
    }

    @Test 
    void normalPath(){
        testOrder.accept();
        assertEquals(OrderStatus.ACCEPTED, testOrder.getStatus());

        testOrder.startPreparing();
        assertEquals(OrderStatus.PREPARING, testOrder.getStatus());

        testOrder.markReady();
        assertEquals(OrderStatus.READY, testOrder.getStatus());
        
    }

    @Test
    void incomingOperationsUseTheStatePattern() {
        testOrder.acceptIncoming();
        assertEquals(OrderStatus.ACCEPTED, testOrder.getStatus());

        testOrder.requireAcceptedForPreparation();
        testOrder.startPreparing();
        assertEquals(OrderStatus.PREPARING, testOrder.getStatus());

        testOrder.markReadyFromPreparation();
        assertEquals(OrderStatus.READY, testOrder.getStatus());
    }

    @Test
    void nonPlacedIncomingOrdersCannotBeAcceptedOrRejected() {
        for (OrderStatus status : OrderStatus.values()) {
            if (status == OrderStatus.PLACED) {
                continue;
            }
            Order order = new Order("100", "123", "456", null, status,
                null, null, null, null, null, testOrder.getLines());

            DomainRuleViolation error = assertThrows(
                DomainRuleViolation.class, order::acceptIncoming
            );

            assertEquals(DomainRuleViolation.Reason.CONFLICT, error.getReason());
            assertEquals("Could not accept order as it was not in the status PLACED", error.getMessage());
            assertEquals(status, order.getStatus());

            DomainRuleViolation rejection = assertThrows(
                DomainRuleViolation.class, () -> order.rejectIncoming("Sold out")
            );
            assertEquals(DomainRuleViolation.Reason.CONFLICT, rejection.getReason());
            assertEquals("Could not reject order as it was not in the status PLACED", rejection.getMessage());
            assertEquals(status, order.getStatus());
        }
    }

    @Test
    void incomingRejectionStillValidatesTheReason() {
        DomainRuleViolation error = assertThrows(
            DomainRuleViolation.class, () -> testOrder.rejectIncoming(" ")
        );
        assertEquals(DomainRuleViolation.Reason.INVALID, error.getReason());
        assertEquals(OrderStatus.PLACED, testOrder.getStatus());

        testOrder.rejectIncoming(" Sold out ");
        assertEquals(OrderStatus.REJECTED, testOrder.getStatus());
        assertEquals("Sold out", testOrder.getRejectReason());
    }

    @Test
    void preparationAndReadyRequireThePreviousStatus() {
        for (OrderStatus status : OrderStatus.values()) {
            Order order = new Order("100", "123", "456", null, status,
                null, null, null, null, null, testOrder.getLines());

            if (status != OrderStatus.ACCEPTED) {
                DomainRuleViolation error = assertThrows(
                    DomainRuleViolation.class, order::requireAcceptedForPreparation
                );
                assertEquals(DomainRuleViolation.Reason.CONFLICT, error.getReason());
            }
            if (status != OrderStatus.PREPARING) {
                DomainRuleViolation error = assertThrows(
                    DomainRuleViolation.class, order::markReadyFromPreparation
                );
                assertEquals(DomainRuleViolation.Reason.CONFLICT, error.getReason());
            }
            assertEquals(status, order.getStatus());
        }
    }

    @Test
    void skippingStep(){
        assertThrows(IllegalTransitionException.class, () -> testOrder.markReady());
        assertEquals(OrderStatus.PLACED, testOrder.getStatus());
    }

    @Test
    void acceptedOrderCannotSkipPreparation(){
        testOrder.accept();

        assertThrows(IllegalTransitionException.class, testOrder::markReady);
        assertEquals(OrderStatus.ACCEPTED, testOrder.getStatus());
    }

    @Test
    void readyRefusesRestaurantActionsAndCustomerCancellation(){
        testOrder.accept();
        testOrder.startPreparing();
        testOrder.markReady();

        assertThrows(IllegalTransitionException.class, testOrder::accept);
        assertThrows(IllegalTransitionException.class, testOrder::startPreparing);
        assertThrows(IllegalTransitionException.class, testOrder::markReady);
        assertThrows(IllegalTransitionException.class, testOrder::cancel);
        assertThrows(DomainRuleViolation.class,() -> testOrder.cancelCustomer("123"));
        assertThrows(IllegalTransitionException.class,() -> testOrder.reject("Sold out"));

        assertEquals(OrderStatus.READY, testOrder.getStatus());
        assertNull(testOrder.getCourierId());
    }

    @Test
    void testDuplicate(){
        //basically trying to put the same in order in the state twice
        testOrder.accept();
        assertThrows(IllegalTransitionException.class,() -> testOrder.accept());

        testOrder.startPreparing();
        assertThrows(IllegalTransitionException.class,() -> testOrder.startPreparing());

        testOrder.markReady();
        assertThrows(IllegalTransitionException.class,() -> testOrder.markReady());
    }
}
