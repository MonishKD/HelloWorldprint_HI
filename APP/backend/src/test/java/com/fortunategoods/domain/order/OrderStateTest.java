package com.fortunategoods.domain.order;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fortunategoods.domain.order.StateDesignPattern.IllegalTransitionException;
import com.fortunategoods.domain.restaurant.MenuItem;
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
    void skippingStep(){
        assertThrows(IllegalTransitionException.class, () -> testOrder.markReady());
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