package com.fortunategoods.application.dashboard;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fortunategoods.domain.order.Order;

/*
    One order as the dashboards show it (#132): what CustomerOrderStatusView and PreparationQueueView send to the frontend.
    Status is lowercase, like every other response the frontend reads. The total is the sum of the lines.
*/
public record DashboardOrder(String orderId, String restaurantId, String customerId, String status,
        String instructions, String deliveryAddress, String rejectReason, List<Line> lines, double total,
        // Left out when null: OperatorOrderResponse unwraps this record and writes its own courierName.
        @JsonInclude(JsonInclude.Include.NON_NULL) String courierName,
        LocalDateTime pickedUpAt, LocalDateTime deliveredAt, Double deliveryLat, Double deliveryLng,
        // R-RVW-01: what the customer gave, or null when the order is not rated.
        Rating rating) {

    // Same fields as before, with no rating: the callers that do not show ratings keep using this one.
    public DashboardOrder(String orderId, String restaurantId, String customerId, String status,
            String instructions, String deliveryAddress, String rejectReason, List<Line> lines, double total,
            String courierName, LocalDateTime pickedUpAt, LocalDateTime deliveredAt, Double deliveryLat, Double deliveryLng) {
        this(orderId, restaurantId, customerId, status, instructions, deliveryAddress, rejectReason, lines, total,
                courierName, pickedUpAt, deliveredAt, deliveryLat, deliveryLng, null);
    }

    public static DashboardOrder of(Order order) {
        return of(order, OrderDelivery.NONE);
    }

    public static DashboardOrder of(Order order, OrderDelivery delivery) {
        return of(order, delivery, null);
    }

    public static DashboardOrder of(Order order, OrderDelivery delivery, Rating rating) {
        List<Line> lines = order.getLines().stream()
                .map(l -> new Line(l.getMenuItemId(), l.getName(), l.getPrice(), l.getOriginalPrice(), l.getQuantity()))
                .toList();
        double total = lines.stream().mapToDouble(l -> l.price() * l.quantity()).sum();
        return new DashboardOrder(order.getId(), order.getRestaurantId(), order.getCustomerId(),
                order.getStatus().name().toLowerCase(Locale.ROOT), order.getSpecialInstructions(),
                order.getDeliveryAddress(), order.getRejectReason(), lines, total, delivery.courierName(),
                delivery.pickedUpAt(), delivery.deliveredAt(), delivery.deliveryLat(), delivery.deliveryLng(), rating);
    }

    public record Rating(int restaurantStars, int courierStars) {}

    // originalPrice is null when the item was not on discount.
    public record Line(String itemId, String name, double price, Double originalPrice, int quantity) {
    }
}
