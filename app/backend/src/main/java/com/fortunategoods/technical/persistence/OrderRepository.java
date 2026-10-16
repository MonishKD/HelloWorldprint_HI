package com.fortunategoods.technical.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.order.OrderStatus;

// Places, finds and changes the status of orders in the database (#23).
// Every status change also saves an event (R-ORD-05).
// This only saves the status. Which status can follow which is in the Domain (R-ORD-04).
@Repository
public class OrderRepository {

    private final JdbcTemplate jdbc;
    private final OrderEventRepository orderEventRepository;

    public OrderRepository(JdbcTemplate jdbc, OrderEventRepository orderEventRepository) {
        this.jdbc = jdbc;
        this.orderEventRepository = orderEventRepository;
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

    // Turns one row of the order_lines table into an OrderLine.
    private OrderLine toOrderLine(ResultSet row) throws SQLException {
        return new OrderLine(
                row.getString("id"),
                row.getString("menu_item_id"),
                row.getInt("quantity"),
                row.getString("name"),
                row.getDouble("price"),
                row.getDouble("original_price"));
    }
}
