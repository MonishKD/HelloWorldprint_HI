package com.fortunategoods.technical.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.fortunategoods.domain.courier.DispatchQueue;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.order.OrderStatus;

/* Turns database rows into Order, OrderLine and WaitingOrder objects (R1: extracted from OrderRepository).
 Shared by OrderQueryRepository (reads) and OrderRepository (lockWaitingForDispatch), so the mapping
 exists once. It depends only on JdbcTemplate and MenuItemRepository, never on a repository class. */
@Component
class OrderHydrator {

    private final JdbcTemplate jdbc;
    private final MenuItemRepository menuItemRepository;

    OrderHydrator(JdbcTemplate jdbc, MenuItemRepository menuItemRepository) {
        this.jdbc = jdbc;
        this.menuItemRepository = menuItemRepository;
    }

    List<OrderLine> findLines(String orderId) {
        String sql = "SELECT * FROM order_lines WHERE order_id = ? ORDER BY id";
        return jdbc.query(sql, (row, rowNumber) -> toOrderLine(row), Integer.parseInt(orderId));
    }

    // Turns one row of the orders table into an Order, with its lines.
    Order toOrder(ResultSet row) throws SQLException {
        String id = row.getString("id");
        return new Order(
                id,
                row.getString("customer_id"),
                row.getString("restaurant_id"),
                row.getString("courier_id"),
                OrderStatus.valueOf(row.getString("status")),
                row.getString("special_instructions"),
                row.getString("delivery_address"),
                row.getObject("delivery_latitude", Double.class),
                row.getObject("delivery_longitude", Double.class),
                row.getString("reject_reason"),
                findLines(id));
    }

    /*  This method turns one row of the order_lines table into an OrderLine, with the MenuItem it points to (#97).
     name, price and original_price stay the copy saved when the order was placed.
     original_price is empty when the item was not on discount, so it must stay null (getDouble would give 0.0).
     It is a numeric column, and the Postgres driver cannot read numeric straight into a Double,
     so it is read as a BigDecimal first. */
    OrderLine toOrderLine(ResultSet row) throws SQLException {
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

    DispatchQueue.WaitingOrder toWaitingOrder(ResultSet row) throws SQLException {
        return new DispatchQueue.WaitingOrder(
            toOrder(row),
            row.getTimestamp("ready_since").toLocalDateTime(),
            row.getLong("ready_event_id"));
    }
}