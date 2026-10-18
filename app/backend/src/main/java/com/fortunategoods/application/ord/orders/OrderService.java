package com.fortunategoods.application.ord.orders;
import com.fortunategoods.domain.order.validation.OrderRequest;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fortunategoods.domain.handlers.ItemAvailabilityHandler;
import com.fortunategoods.domain.handlers.RestaurantStatusHandler;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderEvent;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.order.validation.OrderVerify;
import com.fortunategoods.domain.order.validation.RequestedOrderLineItem;
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


    //helper
    private Order findOrder(String orderId){
        //verifies the id before anything
        OrderVerify.requireId(orderId,"Order ID");
        //gets the order from repo based on the id
        Order order = orderRepo.findById(orderId);

        return order;

    }
    //this functino SHOULD take care of making sure that the order goes in the proper path I puth multiple checks
    //implemented it basically like the cancelOrder steps, just refactored it
    private OrderServiceResult saveTransition(String orderId, Order order, OrderStatus previous){
        //event repo cant return just the top event so you have to get it all THEN get latest
        List<OrderEvent> events = orderEventRepo.findByOrderId(orderId);
        OrderEvent latest = events.get(events.size()-1);
        //safety check here insted of just using the updateStatus functino
        String eventId = orderRepo.updateStatusIfUnchanged(orderId, latest.getId(), previous, order.getStatus());
        //another sanity check here
        if(eventId == null){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT,"Order changed before status was updated!!!!!");
        }
        return new OrderServiceResult(orderId,order.getStatus(),Long.parseLong(latest.getId()));
    }

    //makes it so that if it fails it wont save it
    @Transactional 
    public OrderServiceResult acceptOrder(String orderId){
        Order order = findOrder(orderId);
        //you need this for the saving step cause it'll compare it before saving the transaction
        OrderStatus previous = order.getStatus();

        order.accept();

        return saveTransition(orderId,order,previous);
    }

    @Transactional 
    public OrderServiceResult prepareOrder(String orderId){
        Order order = findOrder(orderId);
        OrderStatus previous = order.getStatus();

        order.startPreparing();

        return saveTransition(orderId,order,previous);
    }

    @Transactional 
    public OrderServiceResult markAsReadyOrder(String orderId){
        Order order = findOrder(orderId);
        OrderStatus previous = order.getStatus();

        order.markReady();

        return saveTransition(orderId,order,previous);
    }
    //could refactor now that we have the saveTransaction but its fine like this TBH
    @Transactional
    public OrderServiceResult cancelOrder(String customerId, String orderId){
        // check validity of IDs provided
        OrderVerify.requireId(customerId, "Customer ID");
        OrderVerify.requireId(orderId, "Order ID");
        // Find the order details from the order repo
        Order order = orderRepo.findById(orderId);

        if (order == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "Order not found");
        }

        // Check if customer trying to cancel order, is customer mentionned in order
        // and check if customer's order status is PLACED
        order.cancelCustomer(customerId);

        // Find the order event of the cancellation
        List<OrderEvent> events = orderEventRepo.findByOrderId(orderId);
        if (events.isEmpty()) {
            throw new IllegalStateException("Order has no event history");
        }

        OrderEvent previousEvent = events.get(events.size() - 1);

        if (previousEvent.getNewStatus() != OrderStatus.PLACED) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT,"Order is no longer placed");
        }

        // Save only if the database order is still PLACED.
        String eventId = orderRepo.updateStatusIfUnchanged(orderId, previousEvent.getId(), OrderStatus.PLACED, OrderStatus.CANCELLED);

        if (eventId == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Order changed before it could be cancelled");
        }

        return new OrderServiceResult(orderId, order.getStatus(), Long.parseLong(eventId));
    }
    



}
