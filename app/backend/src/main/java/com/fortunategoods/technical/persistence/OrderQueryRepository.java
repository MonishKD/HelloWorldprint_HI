package com.fortunategoods.technical.persistence;

import java.sql.Timestamp;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fortunategoods.domain.courier.CourierJob;
import com.fortunategoods.domain.courier.DispatchQueue;
import com.fortunategoods.domain.courier.ReadyOrderSummary;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderStatus;

/* Read-only order queries (R1: extracted from OrderRepository).
 Order lookup, customer/restaurant lists and courier/dispatch display queries live here.
 Writes, payments, courier assignment updates and row locks stay in OrderRepository.
 Row mapping is shared through OrderHydrator, so it exists once. */
@Repository
public class OrderQueryRepository {

    private final JdbcTemplate jdbc;
    private final OrderHydrator hydrator;

    /* The waiting for dispatch SQL line is added to this class.
    This line is used to ready orders with no live offer, oldest ready first, each with its READY event's time and id (R-DLV-04).
    Package-private: OrderRepository.lockWaitingForDispatch reuses it with FOR UPDATE. */
    static final String WAITING_FOR_DISPATCH_SQL = """
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

    public OrderQueryRepository(JdbcTemplate jdbc, OrderHydrator hydrator) {
        this.jdbc = jdbc;
        this.hydrator = hydrator;
    }

    // Returns the order with this id, or null if there is none.
    public Order findById(String id) {
        String sql = "SELECT * FROM orders WHERE id = ?";
        List<Order> found = jdbc.query(sql, (row, rowNumber) -> hydrator.toOrder(row), Integer.parseInt(id));
        if (found.isEmpty()) {
            return null;
        }
        return found.get(0);
    }

    // A customer's orders, oldest first, whatever their status (dashboard read, #132).
    public List<Order> findByCustomer(String customerId) {
        String sql = "SELECT * FROM orders WHERE customer_id = ? ORDER BY id";
        return jdbc.query(sql, (row, rowNumber) -> hydrator.toOrder(row), Integer.parseInt(customerId));
    }

    // Returns a customer's orders, newest first.
    public List<Order> findByCustomerId(String customerId) {
        String sql = "SELECT * FROM orders WHERE customer_id = ? ORDER BY id DESC";
        return jdbc.query(sql, (row, rowNumber) -> hydrator.toOrder(row), Integer.parseInt(customerId));
    }

    // Returns a restaurant's orders in this status, in the order they got to it (R-ORD-03).
    // That time comes from the event that moved each order to this status.
    public List<Order> findByRestaurantAndStatus(String restaurantId, OrderStatus status) {
        String sql = "SELECT orders.* FROM orders "
                + "JOIN order_events ON order_events.order_id = orders.id AND order_events.new_status = orders.status "
                + "WHERE orders.restaurant_id = ? AND orders.status = ? "
                + "ORDER BY order_events.created_at, order_events.id";
        return jdbc.query(sql, (row, rowNumber) -> hydrator.toOrder(row), Integer.parseInt(restaurantId), status.name());
    }

    // A restaurant's orders, oldest first, whatever their status (dashboard read, #132).
    public List<Order> findByRestaurant(String restaurantId) {
        String sql = "SELECT * FROM orders WHERE restaurant_id = ? ORDER BY id";
        return jdbc.query(sql, (row, rowNumber) -> hydrator.toOrder(row), Integer.parseInt(restaurantId));
    }

    // Every order, oldest first, whatever its status (operator all-orders read, #274).
    public List<Order> findAll() {
        String sql = "SELECT * FROM orders ORDER BY id";
        return jdbc.query(sql, (row, rowNumber) -> hydrator.toOrder(row));
    }

    // Read only, for the operator's view of the queue
    public List<DispatchQueue.WaitingOrder> findWaitingForDispatch() {
        return jdbc.query(WAITING_FOR_DISPATCH_SQL, (row, number) -> hydrator.toWaitingOrder(row));
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
        return jdbc.query(sql, (row, number) -> hydrator.toOrder(row));
    }

    /* How many orders each restaurant has in the accepted, preparing and ready statuses, by restaurant id.
    A restaurant with none of these orders is left out of the result. */
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
}