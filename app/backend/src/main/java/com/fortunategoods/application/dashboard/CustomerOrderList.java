package com.fortunategoods.application.dashboard;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.technical.persistence.OrderRepository;

/*
    The signed-in customer's own orders with their details, oldest first. Feeds GET /api/dashboard/orders,
    which the customer pages use. (The A2 CustomerOrderStatusView itself is the /customer read, #135.)
    Reading is not a command (like the console feed), so it is not logged.
*/
@Service
public class CustomerOrderList {
    private final OrderRepository orders;

    public CustomerOrderList(OrderRepository orders) {
        this.orders = orders;
    }

    public List<DashboardOrder> ordersOf(Session session) {
        return orders.findByCustomer(String.valueOf(session.getUserID())).stream().map(DashboardOrder::of).toList();
    }
}
