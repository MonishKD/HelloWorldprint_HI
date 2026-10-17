package com.fortunategoods.domain.order;

import java.util.List;
import java.util.Objects;

public class Order {

    private String id;
    private final String customerId;
    private final String restaurantId;
    private String courierId;
    private OrderStatus status;
    private final String specialInstructions;
    private final String deliveryAddress;
    private final Double deliveryLatitude;
    private final Double deliveryLongitude;
    private String rejectReason;
    private final List<OrderLine> lines;
    //new addition
    private OrderState state;

    public Order(String id, String customerId, String restaurantId, String courierId, OrderStatus status, String specialInstructions, String deliveryAddress, Double deliveryLatitude, Double deliveryLongitude, String rejectReason, List<OrderLine> lines) {
        // Validation checks before respository loads data in
        if (customerId == null || customerId.isBlank()){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Customer is required in an Order");
        }
        if (restaurantId == null || restaurantId.isBlank()){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Restaurant is required in an Order");   
        }

        if (status == null){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Status is required in an Order");            
        }

        if (specialInstructions != null && specialInstructions.length() > 500) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Max character length for Special Instructions: 500");
        }
        
        if (lines == null || lines.isEmpty()) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "An Order must have at least one OrderLine item");
        }

        for (OrderLine line : lines) {

            if (line == null) {
                throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "An order line must not be null");
            }
            String itemRestaurantId = line.getMenuItem().getRestaurantId();

            if (!restaurantId.equals(itemRestaurantId)) {
                throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Order lines must be from the same restaurant as the Order");
            }
        }

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
        this.lines = List.copyOf(lines);
    }

    // New order with initial status = PLACED 
    public static Order placed(String customerId, String restaurantId, String specialInstructions, List<OrderLine> lines){
            return new Order(null, customerId, restaurantId, null, OrderStatus.PLACED, specialInstructions, null, null, null ,null,lines);
    }

    // Setters for non-final fields
    public void setId(String id){
        this.id = Objects.requireNonNull(id, "ID cannot be null");
    }

    public void setCourierId(String courierId){
        this.courierId = courierId;
    }

    public void setRejectReason(String rejectReason){
        this.rejectReason = rejectReason;
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

    public void stateTransition(OrderState state){
        this.state = state;
        //this so that the state and status changes together
        //each State implementation like Accepted returns ACCEPTED for getStatus()
        this.status = state.getStatus();
    }

}
