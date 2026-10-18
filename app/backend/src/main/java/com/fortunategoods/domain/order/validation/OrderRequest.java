package com.fortunategoods.domain.order.validation;
import java.util.List;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
public class OrderRequest {
    

    private Restaurant restaurant;
    private String customerID;
    private MenuItem menuItem;
    private List<OrderLine> orderLines;

    public OrderRequest(Restaurant restaurant, String customerID, MenuItem menuItem, List<OrderLine> orderLines) {
        this.restaurant = restaurant;
        this.customerID = customerID;
        this.menuItem = menuItem;
        this.orderLines = orderLines;
    }

    public Restaurant getRestaurant() {
        return restaurant;
    }

    public String getCustomerID() {
        return customerID;
    }

    public MenuItem getMenuItem() {
        return menuItem;
    }

    public List<OrderLine> getOrderLines() {
        return orderLines;
    }
}

