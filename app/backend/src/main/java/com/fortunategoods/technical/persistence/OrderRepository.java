package com.fortunategoods.technical.persistence;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import com.fortunategoods.domain.courier.CourierJob;
import com.fortunategoods.domain.courier.DispatchQueue;
import com.fortunategoods.domain.courier.ReadyOrderSummary;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.order.OrderStatus;

import java.util.Map;

/* Places, finds and changes the status of orders in the database (#23).
Every status change also saves an event (R-ORD-05).
This only saves the status. Which status can follow which is in the Domain (R-ORD-04). 
*/
@Repository
public class OrderRepository {

    private final JdbcTemplate jdbc;
    private final OrderEventRepository orderEventRepository;
    private final OrderQueryRepository orderQueryRepository;
    private final OrderHydrator hydrator;

    //Updated constructor, where hydrator is added instead.
    public OrderRepository(JdbcTemplate jdbc, OrderEventRepository orderEventRepository, OrderQueryRepository orderQueryRepository, OrderHydrator hydrator) {
        this.jdbc = jdbc;
        this.orderEventRepository = orderEventRepository;
        this.orderQueryRepository = orderQueryRepository;
        this.hydrator = hydrator;
    }

    // Saves the order as PLACED, its lines, and its first event. Returns the new order id.
    // @Transactional means: if one insert fails, nothing is saved.
    @Transactional
    public String place(Order order) {
        String sql = "INSERT INTO orders (customer_id, restaurant_id, status, special_instructions, delivery_address, delivery_latitude, delivery_longitude) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id";
        Integer id = jdbc.queryForObject(sql, Integer.class,
                Integer.parseInt(order.getCustomerId()), Integer.parseInt(order.getRestaurantId()), OrderStatus.PLACED.name(),
                order.getSpecialInstructions(), order.getDeliveryAddress(), order.getDeliveryLatitude(), order.getDeliveryLongitude());

        String lineSql = "INSERT INTO order_lines (order_id, menu_item_id, quantity, name, price, original_price) VALUES (?, ?, ?, ?, ?, ?)";
        for (OrderLine line : order.getLines()) {
            jdbc.update(lineSql, id, Integer.parseInt(line.getMenuItemId()), line.getQuantity(), line.getName(),
                    line.getPrice(), line.getOriginalPrice());
        }

        String orderId = String.valueOf(id);
        orderEventRepository.addFirst(orderId);
        return orderId;
    }

        // Reads moved to OrderQueryRepository (R1). Kept here so existing callers keep working.
    public Order findById(String id) { return orderQueryRepository.findById(id); }

    public List<Order> findByRestaurantAndStatus(String restaurantId, OrderStatus status) { return orderQueryRepository.findByRestaurantAndStatus(restaurantId, status); }

    public List<Order> findByCustomer(String customerId) { return orderQueryRepository.findByCustomer(customerId); }

    public List<Order> findByRestaurant(String restaurantId) { return orderQueryRepository.findByRestaurant(restaurantId); }

    public List<Order> findAll() { return orderQueryRepository.findAll(); }

    public List<Order> findByCustomerId(String customerId) { return orderQueryRepository.findByCustomerId(customerId); }

    public List<DispatchQueue.WaitingOrder> findWaitingForDispatch() { return orderQueryRepository.findWaitingForDispatch(); }

    public List<ReadyOrderSummary> findReadyOrdersForCourier() { return orderQueryRepository.findReadyOrdersForCourier(); }

    public CourierJob findCourierJob(String orderId) { return orderQueryRepository.findCourierJob(orderId); }

    public List<Order> findStalledOrders() { return orderQueryRepository.findStalledOrders(); }

    public Map<String, Map<OrderStatus, Integer>> orderCount() { return orderQueryRepository.orderCount(); }


    // Changes the status and saves the event for it. Returns the new event id, or null if the order does not exist.
    // @Transactional means: the status and the event are saved together, or neither is.
    @Transactional
    public String updateStatus(String orderId, String previousEventId, OrderStatus newStatus) {
        Order order = orderQueryRepository.findById(orderId);
        if (order == null) {
            return null;
        }
        jdbc.update("UPDATE orders SET status = ? WHERE id = ?", newStatus.name(), Integer.parseInt(orderId));
        return orderEventRepository.add(orderId, previousEventId, order.getStatus(), newStatus);
    }

    /*
        This update method prevents from writing changes to DB
        if they were already applied by another request. I.e. a 
        customer logged into two different tabs w/ same account can both 
        press cancel, but only one write to the DB is done as this is transactional
    */
    @Transactional
    public String updateStatusIfUnchanged(String orderId, String prevId, OrderStatus expectedStatus, OrderStatus newStatus){
        String sql = "UPDATE orders SET status = ? WHERE id = ? AND status = ?";
        int changed = jdbc.update(sql, newStatus.name(), Integer.parseInt(orderId), expectedStatus.name());

        if (changed == 0) {
            return null;
        }
        return orderEventRepository.add(orderId, prevId, expectedStatus, newStatus);
    }

    @Transactional
    /*

    */
    public String rejectOrderIfPlaced(String orderId, String prevEventId, String reason){
        String sql = "UPDATE orders SET status = ?, reject_reason = ? WHERE id = ? and status = ?";
        int changed = jdbc.update(sql, OrderStatus.REJECTED.name(), reason, Integer.parseInt(orderId), OrderStatus.PLACED.name());
        // No orders matched with SQL query
        if (changed == 0) {
            return null;
        }
        return orderEventRepository.add(orderId, prevEventId, OrderStatus.PLACED, OrderStatus.REJECTED);
    }

    // Card payment (#259): remembers which Stripe payment paid this order, and the card's brand and last 4 digits.
    public void savePayment(String orderId, String paymentIntentId, String cardBrand, String cardLast4) {
        String sql = "UPDATE orders SET payment_intent_id = ?, card_brand = ?, card_last4 = ? WHERE id = ?";
        jdbc.update(sql, paymentIntentId, cardBrand, cardLast4, Integer.parseInt(orderId));
    }

    // True when an order was already placed with this Stripe payment, so one payment can never pay two orders.
    public boolean isPaymentUsed(String paymentIntentId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE payment_intent_id = ?", Integer.class, paymentIntentId);
        return count != null && count > 0;
    }

    // The Stripe payment of this order, or null when it was placed without card payment.
    public String findPaymentId(String orderId) {
        List<String> found = jdbc.queryForList("SELECT payment_intent_id FROM orders WHERE id = ?", String.class, Integer.parseInt(orderId));
        return found.isEmpty() ? null : found.get(0);
    }

    // The brand of the card that paid this order (for example "visa"), or null when it was placed without card payment.
    public String findCardBrand(String orderId) {
        List<String> found = jdbc.queryForList("SELECT card_brand FROM orders WHERE id = ?", String.class, Integer.parseInt(orderId));
        return found.isEmpty() ? null : found.get(0);
    }

    // The last 4 digits of the card that paid this order, or null when it was placed without card payment.
    public String findCardLast4(String orderId) {
        List<String> found = jdbc.queryForList("SELECT card_last4 FROM orders WHERE id = ?", String.class, Integer.parseInt(orderId));
        return found.isEmpty() ? null : found.get(0);
    }

    /* Thread-locks preparation queue so multiple requests cannot modify the queue while a
    transaction to that queue is still ongoing */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockRestaurantQueue(String restaurantId) {
        jdbc.queryForObject("SELECT id FROM restaurants WHERE id = ? FOR UPDATE", Integer.class, Integer.parseInt(restaurantId));
    }

    // Thread-locks the order during dispatch, so no modifications are made while the assignment is created
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockOrder(String orderId) {
        jdbc.queryForList(
            "SELECT id FROM orders WHERE id = ? FOR UPDATE",
            Integer.class,
            Integer.parseInt(orderId)
        );
    }


    /* Same orders, but thread-locks them until the dispatch transaction ends, so a cancel can't change one
    while it is being offered. If a cancel got there first, Postgres waits for it, sees the order is no
    longer READY and leaves it out. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<DispatchQueue.WaitingOrder> lockWaitingForDispatch() {
        return jdbc.query(OrderQueryRepository.WAITING_FOR_DISPATCH_SQL + " FOR UPDATE OF o", (row, number) -> hydrator.toWaitingOrder(row));
    }

    // Thread-lock method to remove courier from order after assignment is done
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean clearCourierIfUnchanged(String orderId, String courierId) {
        return jdbc.update("""
        UPDATE orders SET courier_id = NULL
        WHERE id = ? AND courier_id = ? AND status = 'READY'
        """, Integer.parseInt(orderId), Integer.parseInt(courierId)) == 1;
    }


    // Sets the courier on an order when they accept its offer. Only works while the order is still READY with no courier.
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean assignCourierIfUnchanged(String orderId, String courierId) {
        return jdbc.update("""
        UPDATE orders SET courier_id = ?
        WHERE id = ? AND courier_id IS NULL AND status = 'READY'
        """, Integer.parseInt(courierId), Integer.parseInt(orderId)) == 1;
    }

    // Thread-lock method to remove courier from order after assignment is cancelled
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean clearCourierAfterCancellation(String orderId, String courierId) {
        return jdbc.update("""
        UPDATE orders SET courier_id = NULL
        WHERE id = ? AND courier_id = ? AND status = 'CANCELLED'
        """, Integer.parseInt(orderId), Integer.parseInt(courierId)) == 1;
    }
}
