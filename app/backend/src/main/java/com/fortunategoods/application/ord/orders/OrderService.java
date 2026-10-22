package com.fortunategoods.application.ord.orders;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.fortunategoods.application.ord.orders.preparation.RegisterPreparationQueue;
import com.fortunategoods.domain.handlers.ItemAvailabilityHandler;
import com.fortunategoods.domain.handlers.RestaurantStatusHandler;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderEvent;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.order.PreparationQueue;
import com.fortunategoods.domain.order.validation.OrderRequest;
import com.fortunategoods.domain.order.validation.OrderVerify;
import com.fortunategoods.domain.order.validation.RequestedOrderLineItem;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.OrderEventRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;

import jakarta.transaction.Transactional;

/*
    Domain class that will handle any business logic backend work.
*/
@Service
public class OrderService {
    private final RestaurantRepository restaurantRepo;
    private final OrderRepository orderRepo;
    private final OrderEventRepository orderEventRepo;
    private final UserRepository userRepo;
    private final RegisterPreparationQueue preparationQueueRegistry;

    public OrderService(RestaurantRepository restaurantRepo, OrderRepository orderRepo, OrderEventRepository orderEventRepo, UserRepository userRepo, RegisterPreparationQueue preparationQueueRegistry) {
        this.restaurantRepo = restaurantRepo;
        this.orderRepo = orderRepo;
        this.orderEventRepo = orderEventRepo;
        this.userRepo = userRepo;
        this.preparationQueueRegistry = preparationQueueRegistry;

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
        //exception handling addition
        if (order == null){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "Order could not be found.");
        }

        return order;

    }
    //this function SHOULD take care of making sure that the order goes in the proper path I puth multiple checks
    //implemented it basically like the cancelOrder steps, just refactored it
    private OrderServiceResult saveTransition(String orderId, Order order, OrderStatus previous){
        String eventId = saveTransitionEvent(orderId, order, previous, "Order changed before status was updated.", "Order changed before status was updated.");
        return new OrderServiceResult(orderId,order.getStatus(),Long.parseLong(eventId), order.getRejectReason());
    }

    private String saveTransitionEvent(String orderId, Order order, OrderStatus previous, String changedMessage, String updateFailedMessage){
        //event repo cant return just the top event so you have to get it all THEN get latest
        List<OrderEvent> events = orderEventRepo.findByOrderId(orderId);
        //check if there are order events
        if (events.isEmpty()){
            throw new IllegalStateException("Order has no event history");
        }
        OrderEvent latest = events.get(events.size()-1);
        //safety check by comparing the latest order status saved in the orderEvent repo to the previous
        if (latest.getNewStatus() != previous){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, changedMessage);
        }
        //safety check here insted of just using the updateStatus function
        String eventId;
        /*
            Since saving transition can lead to different outcomes and different fields being saved
            Check their status and determine which field in the repo to update.
            If the current order status is REJECTED, call the rejectOrderIfPlaced method to update status in repo,
            assuming that the order has a reject reason (if it d/n method won't make any updates).
            Else update the status if unchanged (default update)
        */
        if (order.getStatus() == OrderStatus.REJECTED){
            eventId = orderRepo.rejectOrderIfPlaced(orderId, latest.getId(), order.getRejectReason());
        } else {
            eventId = orderRepo.updateStatusIfUnchanged(orderId, latest.getId(), previous, order.getStatus());
        }

        //another sanity check here
        if(eventId == null){
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, updateFailedMessage);
        }
        return eventId;
    }

    //makes it so that if it fails it wont save it
    @Transactional 
    public OrderServiceResult acceptOrder(String staffId, UserRole role, String orderId){
        Order order = findStaffOrder(staffId, role, orderId, OrdModulePermissions.acceptOrderRoles());

        //you need this for the saving step cause it'll compare it before saving the transaction
        OrderStatus previous = order.getStatus();

        order.acceptIncoming();

        return saveTransition(orderId, order, previous);
    }

    @Transactional
    public OrderServiceResult rejectOrder(String staffId, UserRole role, String orderId, String reason){
        OrdModulePermissions.requireRole(role, OrdModulePermissions.rejectOrderRoles(),"Rejecting orders are only done by restaurant staffs");
        // verify validity of staffId formatting
        OrderVerify.requireId(staffId, "Staff ID");

        // Find order and restaurant
        Order order = findOrder(orderId);
        String restaurantId = userRepo.findRestaurantIdByUserId(staffId);

        // Does order's restaurant match the restaurant trying to reject it
        OrdModulePermissions.requireOwnRestaurant(restaurantId, order.getRestaurantId(), "Cannot reject an order from another restaurant.");

        //you need this for the saving step cause it'll compare it before saving the transaction
        OrderStatus previous = order.getStatus();

        order.rejectIncoming(reason);

        return saveTransition(orderId,order,previous);
    }

    @Transactional
    public OrderServiceResult prepareOrder(String staffId, UserRole role, String orderId) {
        Order order = findStaffOrder(staffId, role, orderId, OrdModulePermissions.preparingOrderRoles());
        order.requireAcceptedForPreparation();

        PreparationQueue queue = preparationQueueRegistry.forRestaurant(order.getRestaurantId());
        // Check if the order we are trying to prepare is the next one in the queue
        queue.requireHead(orderId);

        OrderStatus prev = order.getStatus();
        order.startPreparing();
        OrderServiceResult result = saveTransition(orderId, order, prev);
        queue.dequeue(); // remove the order being prepared from PQ

        return result;
    }

    @Transactional 
    public OrderServiceResult markAsReadyOrder(String staffId, UserRole role, String orderId){
        OrdModulePermissions.requireRole(role, OrdModulePermissions.readyOrderRoles(), "Only restaurant staff can mark orders ready");
        // verify validity of staffId formatting
        OrderVerify.requireId(staffId, "Staff ID");

        // Validate and get the order using the id
        Order order = findOrder(orderId);
        String restaurantId = userRepo.findRestaurantIdByUserId(staffId);

        // Does order's restaurant match the restaurant trying to mark the order as prepared
        OrdModulePermissions.requireOwnRestaurant(restaurantId, order.getRestaurantId(), "Cannot mark an order from another restaurant as ready.");

        OrderStatus previous = order.getStatus();

        order.markReadyFromPreparation();

        return saveTransition(orderId, order, previous);
    }
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

        // Check if customer trying to cancel order, is customer mentionned in order and check if customer's order status is PLACED
        order.cancelCustomer(customerId);

        // Save only if the database order is still PLACED.
        String eventId = saveTransitionEvent(orderId, order, OrderStatus.PLACED, "Order is no longer placed", "Order changed before it could be cancelled");

        return new OrderServiceResult(orderId, order.getStatus(), Long.parseLong(eventId));
    }

    private Order findStaffOrder(String staffId, UserRole role, String orderId, Set<UserRole> allowedRoles) {
        OrdModulePermissions.requireRole(role, allowedRoles, "Only restaurant staff can perform this action");
        // Verify validity of staff id
        OrderVerify.requireId(staffId, "Staff ID");
        // Find the order in the repository
        Order order = findOrder(orderId);
        // Find the restaurant from the staffId
        String restaurantId = userRepo.findRestaurantIdByUserId(staffId);
        // If order's restaurant and staff's restaurant don't match, throw exception
        OrdModulePermissions.requireOwnRestaurant(restaurantId, order.getRestaurantId(), "You can only change your own restaurant's orders");
        // Lock the queue to give time to finish transaction
        orderRepo.lockRestaurantQueue(restaurantId);

        // Another transaction may have changed the order before we got the lock.
        Order currentOrder = findOrder(orderId);

        if (!restaurantId.equals(currentOrder.getRestaurantId())) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Order changed before the action could be completed");
        }
        return currentOrder;
    }
    
}
