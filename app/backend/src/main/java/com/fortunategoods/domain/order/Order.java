package com.fortunategoods.domain.order;

import java.util.List;

public class Order {

    private String id;
    private String customerId;
    private String restaurantId;
    private String courierId;
    private OrderStatus status;
    private String specialInstructions;
    private String deliveryAddress;
    private Double deliveryLatitude;
    private Double deliveryLongitude;
    private String rejectReason;
    private List<OrderLine> lines;

    public Order(String id, String customerId, String restaurantId, String courierId, OrderStatus status,
            String specialInstructions, String deliveryAddress, Double deliveryLatitude, Double deliveryLongitude,
            String rejectReason, List<OrderLine> lines) {
        this.id = id;
        this.customerId = customerId;
        this.restaurantId = restaurantId;
        this.courierId = courierId;
        this.status = status;
        this.specialInstructions = specialInstructions;
        this.deliveryAddress = deliveryAddress;
        this.deliveryLatitude = deliveryLatitude;
        this.deliveryLongitude = deliveryLongitude;
        this.rejectReason = rejectReason;
        this.lines = lines;
    }

    public String getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getRestaurantId() {
        return restaurantId;
    }

    public String getCourierId() {
        return courierId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getSpecialInstructions() {
        return specialInstructions;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public Double getDeliveryLatitude() {
        return deliveryLatitude;
    }

    public Double getDeliveryLongitude() {
        return deliveryLongitude;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public List<OrderLine> getLines() {
        return lines;
    }

}
