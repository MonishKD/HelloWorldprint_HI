package com.fortunategoods.application.dashboard;

import java.util.List;
import java.util.Locale;

import com.fortunategoods.domain.order.Order;

/*
    One order as the dashboards show it (#132): what CustomerOrderStatusView and PreparationQueueView send to the frontend.
    Status is lowercase, like every other response the frontend reads. The total is the sum of the lines.
*/
public record DashboardOrder(String orderId, String restaurantId, String customerId, String status,
        String instructions, String deliveryAddress, String rejectReason, List<Line> lines, double total) {

    public static DashboardOrder of(Order order) {
        List<Line> lines = order.getLines().stream()
                .map(l -> new Line(l.getMenuItemId(), l.getName(), l.getPrice(), l.getOriginalPrice(), l.getQuantity()))
                .toList();
        double total = lines.stream().mapToDouble(l -> l.price() * l.quantity()).sum();
        return new DashboardOrder(order.getId(), order.getRestaurantId(), order.getCustomerId(),
                order.getStatus().name().toLowerCase(Locale.ROOT), order.getSpecialInstructions(),
                order.getDeliveryAddress(), order.getRejectReason(), lines, total);
    }

    // originalPrice is null when the item was not on discount.
    public record Line(String itemId, String name, double price, Double originalPrice, int quantity) {
    }
}
