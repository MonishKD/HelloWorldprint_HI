package com.fortunategoods.application.ord;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fortunategoods.domain.handlers.ItemAvailabilityHandler;
import com.fortunategoods.domain.handlers.RestaurantStatusHandler;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderEvent;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.order.OrderServiceResult;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.order.OrderVerify;
import com.fortunategoods.domain.order.RequestedOrderLineItem;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.technical.persistence.OrderEventRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;

import jakarta.transaction.Transactional;

/*
    Domain class that will handle any business logic backend work.
*/
@Service
public class OrderService {
    private final RestaurantRepository restaurantRepo;
    private final OrderRepository orderRepo;
    private final OrderEventRepository orderEventRepo;

    public OrderService(RestaurantRepository restaurantRepo, OrderRepository orderRepo, OrderEventRepository orderEventRepo) {
        this.restaurantRepo = restaurantRepo;
        this.orderRepo = orderRepo;
        this.orderEventRepo = orderEventRepo;
    }
    
    @Transactional 
    public OrderServiceResult placeOrder(String customerId, String restaurantId, List<RequestedOrderLineItem> requestedItems, String instructions, String deliveryAddress) {
        // Verify validity of customer id and restaurant id
        OrderVerify.requireId(customerId, "Customer ID");
        OrderVerify.requireId(restaurantId, "Restaurant ID");

        // Find the restaurant
        Restaurant restaurant = restaurantRepo.findById(restaurantId);

        // Verify validity of the order lines
        List<OrderLine> lines = OrderVerify.prepareLines(restaurant, requestedItems);


        // Chain of responsibility
        RestaurantStatusHandler chain = new RestaurantStatusHandler();
        chain.setNextHandler(new ItemAvailabilityHandler());
        OrderRequest validationRequest = new OrderRequest(restaurant, customerId, null, lines);
        OrderVerify.validate(validationRequest, chain);

        // After performing chain of responsibility, create an order with state PLACED
        Order order = OrderVerify.createPlacedOrder(customerId, restaurantId, instructions, deliveryAddress, lines);

        String orderId = orderRepo.place(order); // registers an OrderEvent in the orderEventRepo

        List<OrderEvent> events = orderEventRepo.findByOrderId(orderId);

        if (events.size() != 1) {
            throw new IllegalStateException("A new order must have one initial event");
        }

        OrderEvent event = events.get(0);

        if (!orderId.equals(event.getOrderId()) || event.getNewStatus() != OrderStatus.PLACED) {
            throw new IllegalStateException( "Initial order event is wrong");
        }

        return new OrderServiceResult(orderId, OrderStatus.PLACED, Long.parseLong(event.getId()));
    }
}
