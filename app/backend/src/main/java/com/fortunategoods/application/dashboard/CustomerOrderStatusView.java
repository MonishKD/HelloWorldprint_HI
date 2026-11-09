package com.fortunategoods.application.dashboard;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fortunategoods.domain.order.Order;

/*
    The customer dashboard: the signed-in customer's own orders with their current status, newest first.
    Field names match what the frontend's adapters.ts reads for an order.
    status is lowercase ("placed"), like the other order responses.
*/
public record CustomerOrderStatusView(List<OrderRow> orders) {

    public CustomerOrderStatusView {
        orders = List.copyOf(orders);
    }

    public record OrderRow(String id, String restaurantId, String restaurantName, String status, double total,
            String deliveryAddress, String specialInstructions, String rejectReason, List<LineRow> lines,
            String courierName, LocalDateTime pickedUpAt, LocalDateTime deliveredAt, Double deliveryLat, Double deliveryLng) {
    }

    // name and price are the copy saved when the order was placed.
    public record LineRow(String menuItemId, String name, int quantity, double price, Double originalPrice) {
    }

    // restaurantNames: restaurant id -> name, for the restaurants these orders come from.
    // deliveries: order id -> how its delivery is going.
    public static CustomerOrderStatusView of(List<Order> orders, Map<String, String> restaurantNames, Map<String, OrderDelivery> deliveries) {
        return new CustomerOrderStatusView(orders.stream()
                .map(order -> row(order, restaurantNames.get(order.getRestaurantId()), deliveries.get(order.getId())))
                .toList());
    }

    private static OrderRow row(Order order, String restaurantName, OrderDelivery delivery) {
        List<LineRow> lines = order.getLines().stream()
                .map(line -> new LineRow(line.getMenuItemId(), line.getName(), line.getQuantity(), line.getPrice(), line.getOriginalPrice()))
                .toList();
        double total = order.getLines().stream().mapToDouble(line -> line.getPrice() * line.getQuantity()).sum();
        return new OrderRow(order.getId(), order.getRestaurantId(), restaurantName,
                order.getStatus().name().toLowerCase(Locale.ROOT), total, order.getDeliveryAddress(),
                order.getSpecialInstructions(), order.getRejectReason(), lines, delivery.courierName(),
                delivery.pickedUpAt(), delivery.deliveredAt(), delivery.deliveryLat(), delivery.deliveryLng());
    }
}
