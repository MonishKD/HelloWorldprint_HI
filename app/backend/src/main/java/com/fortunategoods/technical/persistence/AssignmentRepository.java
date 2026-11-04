package com.fortunategoods.technical.persistence;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fortunategoods.domain.courier.Assignment;

/*
    Assignment Repository which will act as a junction table between Orders and Couriers
*/
@Repository
public class AssignmentRepository {
    private final JdbcTemplate jdbc;

    public AssignmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Method to check if an order has been offered or accepted and not delivered
    public Assignment findActiveByOrder(String orderId) {
        // Find assignment for an order_id that has ben offered/accepted but not delivered
        String sql = """
        SELECT * FROM assignments
        WHERE order_id = ?
        AND status IN ('OFFERED', 'ACCEPTED')
        AND is_delivered = FALSE
        """;

        List<Assignment> found = jdbc.query(sql, (row, number) -> new Assignment(
            row.getString("id"),
            row.getString("order_id"),
            row.getString("courier_id"),
            Assignment.Status.valueOf(row.getString("status")),
            row.getBoolean("is_picked_up"),
            row.getBoolean("is_delivered")
        ), Integer.parseInt(orderId));

        return found.isEmpty() ? null : found.get(0);
    }

    // Method to create an assignment in the DB and return it in the backend
    @Transactional(propagation = Propagation.MANDATORY)
    public Assignment add(Assignment offer) {
        String sql = """
        INSERT INTO assignments (order_id, courier_id, status, is_picked_up, is_delivered)
        VALUES (?, ?, ?, ?, ?)
        RETURNING id
        """;

        Integer id = jdbc.queryForObject(
            sql,
            Integer.class,
            Integer.parseInt(offer.getOrderId()),
            Integer.parseInt(offer.getCourierId()),
            offer.getStatus().name(),
            offer.isPickedUp(),
            offer.isDelivered()
        );

        return new Assignment(String.valueOf(id), offer.getOrderId(), offer.getCourierId(), offer.getStatus(), offer.isPickedUp(), offer.isDelivered());
    }
}
