package com.fortunategoods.application.ord;
import java.util.List;
import com.fortunategoods.domain.order.OrderLine;
public class OrderRequest {
    

    private String restaurantID;
    private String customerID;
    private List<OrderLine> orderLines;

    public OrderRequest(String restaurantID, String customerID, List<OrderLine> orderLines) {
        this.restaurantID = restaurantID;
        this.customerID = customerID;
        this.orderLines = orderLines;
    }

    public String getRestaurantID() {
        return restaurantID;
    }

    public String getCustomerID() {
        return customerID;
    }

    public List<OrderLine> getOrderLines() {
        return orderLines;
    }
}

