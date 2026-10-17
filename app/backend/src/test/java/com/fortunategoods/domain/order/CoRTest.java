package com.fortunategoods.domain.order;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fortunategoods.application.ord.OrderRequest;
import com.fortunategoods.domain.handlers.BaseOrderHandler;
import com.fortunategoods.domain.handlers.ItemAvailabilityHandler;
import com.fortunategoods.domain.handlers.RestaurantStatusHandler;
import com.fortunategoods.domain.restaurant.MenuItem;
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.application.ord.OrderService;

public class CoRTest {
    private BaseOrderHandler restaurantHandler;
    private OrderService orderService;
    //https://www.baeldung.com/junit-before-beforeclass-beforeeach-beforeall
   @BeforeEach
   void init(){
    restaurantHandler = new RestaurantStatusHandler();
    BaseOrderHandler itemHandler = new ItemAvailabilityHandler();
    //needs this so that item handler goes next
    restaurantHandler.setNextHandler(itemHandler);
    orderService = new OrderService(restaurantHandler);

   }
   

    @Test
    void testRestaurantClosed(){
        List<MenuItem> menu = new ArrayList<>();
        menu.add(new MenuItem("m1", "r1", "Wrap", MenuItemStatus.AVAILABLE, 4.50));
        //have to match the restaurant IDs
        Restaurant restaurant = new Restaurant("r1","name",RestaurantStatus.CLOSED,1,2,menu);
        //random for orderLines since not testing that
        OrderRequest orderReq = new OrderRequest(restaurant, "12345678", menu.get(0), new ArrayList<>());

        assertThrows(IllegalStateException.class,() -> { orderService.placeOrder(orderReq); });


    }

    @Test
    void testRestaurantOpen(){
         List<MenuItem> menu = new ArrayList<>();
        menu.add(new MenuItem("m1", "r2", "Wrap", MenuItemStatus.AVAILABLE, 4.50));
        //have to match the restaurant IDs
        Restaurant restaurant = new Restaurant("r2","name",RestaurantStatus.OPEN,1,2,menu);
        //random for orderLines since not testing that
        OrderRequest orderReq = new OrderRequest(restaurant, "12345678", menu.get(0), new ArrayList<>());
        //wont return true here since sending it to the next handler, so doing assertDoesNotThrow
        assertDoesNotThrow(() -> { orderService.placeOrder(orderReq); });
    }

    @Test 
    void testItemSoldOut(){
         List<MenuItem> menu = new ArrayList<>();
        menu.add(new MenuItem("m1", "r3", "Wrap", MenuItemStatus.SOLD_OUT, 4.50));
        
        Restaurant restaurant = new Restaurant("r3","name",RestaurantStatus.OPEN,1,2,menu);
        //need to actually create OrderLine now and then the list of order lines for the order request
        OrderLine orderLine = new OrderLine("1", "m1", 1, "name", 0.0, 0.0, menu.get(0));
        List<OrderLine> orderLines = new ArrayList<>();
        orderLines.add(orderLine);
        OrderRequest orderReq = new OrderRequest(restaurant, "12345678", menu.get(0), orderLines);
        //calling the restaurant handler first since its gonna call the menuHandler after
        assertThrows(IllegalStateException.class,() -> {  orderService.placeOrder(orderReq);});

    }

    @Test
    void testItemAvailable(){
          List<MenuItem> menu = new ArrayList<>();
        menu.add(new MenuItem("m1", "r4", "Wrap", MenuItemStatus.AVAILABLE, 4.50));
        
        Restaurant restaurant = new Restaurant("r4","name",RestaurantStatus.OPEN,1,2,menu);
        //need to actually create OrderLine now and then the list of order lines for the order request
        OrderLine orderLine = new OrderLine("1", "m1", 1, "name", 0.0, 0.0, menu.get(0));
        List<OrderLine> orderLines = new ArrayList<>();
        orderLines.add(orderLine);
        OrderRequest orderReq = new OrderRequest(restaurant, "12345678", menu.get(0), orderLines);
        //calling the restaurant handler first since its gonna call the menuHandler after
        assertTrue( orderService.placeOrder(orderReq));
    }

    @Test 
    void testItemWasAvailableThenSoldOut(){
        List<MenuItem> menu = new ArrayList<>();
        menu.add(new MenuItem("m1", "r5", "Wrap", MenuItemStatus.AVAILABLE, 4.50));
        
        Restaurant restaurant = new Restaurant("r5","name",RestaurantStatus.OPEN,1,2,menu);
        //making it sold out after
        menu.get(0).setStatus(MenuItemStatus.SOLD_OUT);

        OrderLine orderLine = new OrderLine("1", "m1", 1, "name", 0.0, 0.0, menu.get(0));
        List<OrderLine> orderLines = new ArrayList<>();
        orderLines.add(orderLine);
        OrderRequest orderReq = new OrderRequest(restaurant, "12345678", menu.get(0), orderLines);
        //calling the restaurant handler first since its gonna call the menuHandler after
        assertThrows(IllegalStateException.class,() -> {  orderService.placeOrder(orderReq);});
    }

    
}
