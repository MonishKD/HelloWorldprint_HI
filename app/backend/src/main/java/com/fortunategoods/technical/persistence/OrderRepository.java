package com.fortunategoods.technical.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

import java.sql.Timestamp;


// Places, finds and changes the status of orders in the database (#23).
// Every status change also saves an event (R-ORD-05).
// This only saves the status. Which status can follow which is in the Domain (R-ORD-04).
@Repository
public class OrderRepository {

    private final JdbcTemplate jdbc;
    private final OrderEventRepository orderEventRepository;
    private final MenuItemRepository menuItemRepository;

    // Spring gives us these. We use menuItemRepository to give each order line its MenuItem.
    public OrderRepository(JdbcTemplate jdbc, OrderEventRepository orderEventRepository, MenuItemRepository menuItemRepository) {
        this.jdbc = jdbc;
        this.orderEventRepository = orderEventRepository;
        this.menuItemRepository = menuItemRepository;
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

    // Returns the order with this id, or null if there is none.
    public Order findById(String id) {
        String sql = "SELECT * FROM orders WHERE id = ?";
        List<Order> found = jdbc.query(sql, (row, rowNumber) -> toOrder(row), Integer.parseInt(id));
        if (found.isEmpty()) {
            return null;
        }
        return found.get(0);
    }

    // Changes the status and saves the event for it. Returns the new event id, or null if the order does not exist.
    // @Transactional means: the status and the event are saved together, or neither is.
    @Transactional
    public String updateStatus(String orderId, String previousEventId, OrderStatus newStatus) {
        Order order = findById(orderId);
        if (order == null) {
            return null;
        }
        jdbc.update("UPDATE orders SET status = ? WHERE id = ?", newStatus.name(), Integer.parseInt(orderId));
        return orderEventRepository.add(orderId, previousEventId, order.getStatus(), newStatus);
    }

    // Returns a restaurant's orders in this status, in the order they got to it (R-ORD-03).
    // That time comes from the event that moved each order to this status.
    public List<Order> findByRestaurantAndStatus(String restaurantId, OrderStatus status) {
        String sql = "SELECT orders.* FROM orders "
                + "JOIN order_events ON order_events.order_id = orders.id AND order_events.new_status = orders.status "
                + "WHERE orders.restaurant_id = ? AND orders.status = ? "
                + "ORDER BY order_events.created_at, order_events.id";
        return jdbc.query(sql, (row, rowNumber) -> toOrder(row), Integer.parseInt(restaurantId), status.name());
    }

    // A customer's orders, oldest first, whatever their status (dashboard read, #132).
    public List<Order> findByCustomer(String customerId) {
        String sql = "SELECT * FROM orders WHERE customer_id = ? ORDER BY id";
        return jdbc.query(sql, (row, rowNumber) -> toOrder(row), Integer.parseInt(customerId));
    }

    // A restaurant's orders, oldest first, whatever their status (dashboard read, #132).
    public List<Order> findByRestaurant(String restaurantId) {
        String sql = "SELECT * FROM orders WHERE restaurant_id = ? ORDER BY id";
        return jdbc.query(sql, (row, rowNumber) -> toOrder(row), Integer.parseInt(restaurantId));
    }

    // Every order, oldest first, whatever its status (operator all-orders read, #274).
    public List<Order> findAll() {
        String sql = "SELECT * FROM orders ORDER BY id";
        return jdbc.query(sql, (row, rowNumber) -> toOrder(row));
    }

    // Returns a customer's orders, newest first.
    public List<Order> findByCustomerId(String customerId) {
        String sql = "SELECT * FROM orders WHERE customer_id = ? ORDER BY id DESC";
        return jdbc.query(sql, (row, rowNumber) -> toOrder(row), Integer.parseInt(customerId));
    }

    private List<OrderLine> findLines(String orderId) {
        String sql = "SELECT * FROM order_lines WHERE order_id = ? ORDER BY id";
        return jdbc.query(sql, (row, rowNumber) -> toOrderLine(row), Integer.parseInt(orderId));
    }

    // Turns one row of the orders table into an Order, with its lines.
    private Order toOrder(ResultSet row) throws SQLException {
        String id = row.getString("id");
        return new Order(
                id,
                row.getString("customer_id"),
                row.getString("restaurant_id"),
                row.getString("courier_id"),
                OrderStatus.valueOf(row.getString("status")),
                row.getString("special_instructions"),
                row.getString("delivery_address"),
                row.getDouble("delivery_latitude"),
                row.getDouble("delivery_longitude"),
                row.getString("reject_reason"),
                findLines(id));
    }

    // Turns one row of the order_lines table into an OrderLine, with the MenuItem it points to (#97).
    // name, price and original_price stay the copy saved when the order was placed.
    // original_price is empty when the item was not on discount, so it must stay null (getDouble would give 0.0).
    // It is a numeric column, and the Postgres driver cannot read numeric straight into a Double,
    // so it is read as a BigDecimal first.
    private OrderLine toOrderLine(ResultSet row) throws SQLException {
        String menuItemId = row.getString("menu_item_id");
        BigDecimal originalPrice = row.getBigDecimal("original_price");
        return new OrderLine(
                row.getString("id"),
                menuItemId,
                row.getInt("quantity"),
                row.getString("name"),
                row.getDouble("price"),
                originalPrice == null ? null : originalPrice.doubleValue(),
                menuItemRepository.findById(menuItemId));
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

    // Thread-locks preparation queue so multiple requests cannot modify the queue while a
    // transaction to that queue is still ongoing
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

    // Ready orders with no live offer, oldest ready first, each with its READY event's time and id (R-DLV-04)
    private static final String WAITING_FOR_DISPATCH_SQL = """
            SELECT o.*, e.created_at AS ready_since, e.id AS ready_event_id
            FROM orders o
            JOIN order_events e
            ON e.order_id = o.id AND e.new_status = 'READY'
            WHERE o.status = 'READY'
            AND NOT EXISTS (
                SELECT 1 FROM assignments a
                WHERE a.order_id = o.id
                AND a.status IN ('OFFERED', 'ACCEPTED')
                AND a.is_delivered = FALSE
            )
            ORDER BY e.created_at, e.id
            """;

    // Read only, for the operator's view of the queue
    public List<DispatchQueue.WaitingOrder> findWaitingForDispatch() {
        return jdbc.query(WAITING_FOR_DISPATCH_SQL, (row, number) -> toWaitingOrder(row));
    }

    public List<ReadyOrderSummary> findReadyOrdersForCourier() {
        String sql = """
        SELECT o.id,
               r.name AS restaurant_name,
               r.address AS pickup_address,
               (SELECT COALESCE(SUM(quantity), 0) FROM order_lines WHERE order_id = o.id) AS item_count,
               e.created_at AS ready_since
        FROM orders o
        JOIN restaurants r ON r.id = o.restaurant_id
        JOIN order_events e ON e.order_id = o.id AND e.new_status = 'READY'
        WHERE o.status = 'READY'
        ORDER BY e.created_at, e.id
        """;

        return jdbc.query(sql, (row, number) -> new ReadyOrderSummary(
            row.getString("id"),
            row.getString("restaurant_name"),
            row.getString("pickup_address"),
            row.getInt("item_count"),
            row.getTimestamp("ready_since").toLocalDateTime()
        ));
    }

    public CourierJob findCourierJob(String orderId) {
        String sql = """
        SELECT o.id,
               o.status,
               o.special_instructions,
               o.delivery_address,
                o.delivery_latitude,
                o.delivery_longitude,
                r.name AS restaurant_name,
                r.address AS pickup_address,
                r.latitude AS pickup_latitude,
                r.longitude AS pickup_longitude,
                c.name AS customer_name,
                p.created_at AS picked_up_at
            FROM orders o
            JOIN restaurants r ON r.id = o.restaurant_id
            LEFT JOIN customers c ON c.user_id = o.customer_id
            LEFT JOIN order_events p ON p.order_id = o.id AND p.new_status = 'PICKED_UP'
            WHERE o.id = ? AND o.status IN ('READY', 'PICKED_UP')   
               """;

        List<CourierJob.Line> lines = jdbc.query(
       "SELECT name, quantity FROM order_lines WHERE order_id = ? ORDER BY id",
       (row, number) -> new CourierJob.Line(row.getString("name"), row.getInt("quantity")),
       Integer.parseInt(orderId));

       List<CourierJob> found = jdbc.query(sql, (row, number) -> {
        Timestamp pickedUp = row.getTimestamp("picked_up_at");
        return new CourierJob(
            row.getString("id"),
            row.getString("status"),
            row.getString("restaurant_name"),
            row.getString("pickup_address"),
            row.getObject("pickup_latitude", Double.class),
            row.getObject("pickup_longitude", Double.class),
            row.getString("customer_name"),
            row.getString("delivery_address"),
            row.getObject("delivery_latitude", Double.class),
            row.getObject("delivery_longitude", Double.class),
            lines,
            row.getString("special_instructions"),
            pickedUp == null ? null : pickedUp.toLocalDateTime()
        );
       }, Integer.parseInt(orderId));

       return found.isEmpty() ? null : found.get(0);
    }




    // Same orders, but thread-locks them until the dispatch transaction ends, so a cancel can't change one
    // while it is being offered. If a cancel got there first, Postgres waits for it, sees the order is no
    // longer READY and leaves it out.
    @Transactional(propagation = Propagation.MANDATORY)
    public List<DispatchQueue.WaitingOrder> lockWaitingForDispatch() {
        return jdbc.query(WAITING_FOR_DISPATCH_SQL + " FOR UPDATE OF o", (row, number) -> toWaitingOrder(row));
    }

    private DispatchQueue.WaitingOrder toWaitingOrder(ResultSet row) throws SQLException {
        return new DispatchQueue.WaitingOrder(
            toOrder(row),
            row.getTimestamp("ready_since").toLocalDateTime(),
            row.getLong("ready_event_id"));
    }

    // Method used to find orders that are assigned to offline couriers (stalled)
    public List<Order> findStalledOrders() {
        String sql = """
        SELECT o.*
        FROM orders o
        JOIN assignments a
            ON a.order_id = o.id AND a.courier_id = o.courier_id
        JOIN couriers c ON c.user_id = a.courier_id
        WHERE o.status = 'READY'
        AND a.status = 'ACCEPTED'
        AND a.is_picked_up = FALSE
        AND a.is_delivered = FALSE
        AND c.status = 'OFFLINE'
        ORDER BY o.id
        """;
        return jdbc.query(sql, (row, number) -> toOrder(row));
    }

    // Thread-lock method to remove courier from order after assignment is done
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean clearCourierIfUnchanged(String orderId, String courierId) {
        return jdbc.update("""
        UPDATE orders SET courier_id = NULL
        WHERE id = ? AND courier_id = ? AND status = 'READY'
        """, Integer.parseInt(orderId), Integer.parseInt(courierId)) == 1;
    }

    // How many orders each restaurant has in the accepted, preparing and ready statuses, by restaurant id.
    // A restaurant with none of these orders is left out of the result.
    public Map<String, Map<OrderStatus, Integer>> orderCount() {
        String sql = """
            SELECT restaurant_id, status, COUNT(*) AS total
            FROM orders
            WHERE status IN ('ACCEPTED', 'PREPARING', 'READY')
            GROUP BY restaurant_id, status
            """;

        Map<String, Map<OrderStatus, Integer>> counts = new HashMap<>();
        jdbc.query(sql, row -> {
            counts.computeIfAbsent(row.getString("restaurant_id"), id -> new EnumMap<>(OrderStatus.class))
                .put(OrderStatus.valueOf(row.getString("status")), row.getInt("total"));
        });
        return counts;
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
