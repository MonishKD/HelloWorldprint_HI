package com.fortunategoods.application.dashboard;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.technical.persistence.OrderRepository;

/*
    A2 CustomerOrderStatusView (#132): the signed-in customer's own orders and their current state, oldest first.
    Reading is not a command (like the console feed), so it is not logged.
*/
@Service
public class CustomerOrderStatusView {
    private final OrderRepository orders;

    public CustomerOrderStatusView(OrderRepository orders) {
        this.orders = orders;
    }

    public List<DashboardOrder> ordersOf(Session session) {
        return orders.findByCustomer(String.valueOf(session.getUserID())).stream().map(DashboardOrder::of).toList();
    }
}
