package com.fortunategoods.technical.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fortunategoods.domain.order.OrderEvent;
import com.fortunategoods.domain.order.OrderStatus;

// Saves and finds order events in the database (#23, R-ORD-05).
// An event is one status change of an order, like PLACED -> ACCEPTED. Events are only added, never changed.
@Repository
public class OrderEventRepository {

    private final JdbcTemplate jdbc;

    public OrderEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Saves the first event of an order (it was just placed). Returns the new event id.
    public String addFirst(String orderId) {
        String sql = "INSERT INTO order_events (order_id, new_status) VALUES (?, ?) RETURNING id";
        Integer id = jdbc.queryForObject(sql, Integer.class, Integer.parseInt(orderId), OrderStatus.PLACED.name());
        return String.valueOf(id);
    }

    // Saves a status change. Returns the new event id.
    public String add(String orderId, String previousEventId, OrderStatus previousStatus, OrderStatus newStatus) {
        String sql = "INSERT INTO order_events (order_id, previous_event_id, previous_status, new_status) VALUES (?, ?, ?, ?) RETURNING id";
        Integer id = jdbc.queryForObject(sql, Integer.class,
                Integer.parseInt(orderId), Integer.parseInt(previousEventId), previousStatus.name(), newStatus.name());
        return String.valueOf(id);
    }

    // Returns all events of an order, oldest first.
    public List<OrderEvent> findByOrderId(String orderId) {
        String sql = "SELECT * FROM order_events WHERE order_id = ? ORDER BY id";
        return jdbc.query(sql, (row, rowNumber) -> toOrderEvent(row), Integer.parseInt(orderId));
    }

    // Turns one row of the order_events table into an OrderEvent.
    // The first event has no previous status, so it stays null.
    private OrderEvent toOrderEvent(ResultSet row) throws SQLException {
        OrderStatus previousStatus = null;
        if (row.getString("previous_status") != null) {
            previousStatus = OrderStatus.valueOf(row.getString("previous_status"));
        }
        return new OrderEvent(
                row.getString("id"),
                row.getString("order_id"),
                row.getString("previous_event_id"),
                previousStatus,
                OrderStatus.valueOf(row.getString("new_status")),
                row.getTimestamp("created_at").toLocalDateTime());
    }
}
