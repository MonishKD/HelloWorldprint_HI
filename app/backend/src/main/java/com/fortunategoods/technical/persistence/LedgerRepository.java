package com.fortunategoods.technical.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.pricing.LedgerEntry;

// Saves and reads ledger entries in the database (#236, R-PRC-03).
// One row in ledger_entries is one LedgerEntry, with the same fields.
// The ledger is insert-only: there is no update or delete here on purpose. A refund (#266) is a new REFUND row.
// Every read returns the newest entries first.
@Repository
public class LedgerRepository {

    private final JdbcTemplate jdbc;

    public LedgerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Saves every field LedgerEntry carries. LedgerService (#261) calls this when an order ends.
    public void save(LedgerEntry ledgerEntry) {
        String sql = "INSERT INTO ledger_entries (order_id, entry_type, final_status, subtotal, delivery_fee, service_fee, "
                + "surcharge, amount, placed_at, finished_at, payment_reference) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbc.update(sql, Integer.parseInt(ledgerEntry.getOrderId()), ledgerEntry.getType().name(),
                ledgerEntry.getFinalStatus().name(), ledgerEntry.getSubtotalCents(), ledgerEntry.getDeliveryFeeCents(),
                ledgerEntry.getServiceFeeCents(), ledgerEntry.getSurchargeCents(), ledgerEntry.getAmountCents(),
                Timestamp.valueOf(ledgerEntry.getPlacedAt()), Timestamp.valueOf(ledgerEntry.getFinishedAt()),
                ledgerEntry.getPaymentReference());
    }

    // Every entry, for the operator.
    public List<LedgerEntry> findAll() {
        String sql = "SELECT * FROM ledger_entries ORDER BY id DESC";
        return jdbc.query(sql, (row, rowNumber) -> toLedgerEntry(row));
    }

    // The entries of the orders this customer placed.
    public List<LedgerEntry> findByCustomer(String customerId) {
        String sql = "SELECT ledger_entries.* FROM ledger_entries JOIN orders ON orders.id = ledger_entries.order_id "
                + "WHERE orders.customer_id = ? ORDER BY ledger_entries.id DESC";
        return jdbc.query(sql, (row, rowNumber) -> toLedgerEntry(row), Integer.parseInt(customerId));
    }

    // The entries of the orders placed at this restaurant.
    public List<LedgerEntry> findByRestaurant(String restaurantId) {
        String sql = "SELECT ledger_entries.* FROM ledger_entries JOIN orders ON orders.id = ledger_entries.order_id "
                + "WHERE orders.restaurant_id = ? ORDER BY ledger_entries.id DESC";
        return jdbc.query(sql, (row, rowNumber) -> toLedgerEntry(row), Integer.parseInt(restaurantId));
    }

    // The entries of the orders this courier delivered. Who delivered an order is in assignments.
    public List<LedgerEntry> findByCourier(String courierId) {
        String sql = "SELECT ledger_entries.* FROM ledger_entries "
                + "JOIN assignments ON assignments.order_id = ledger_entries.order_id "
                + "WHERE assignments.courier_id = ? AND assignments.is_delivered = TRUE ORDER BY ledger_entries.id DESC";
        return jdbc.query(sql, (row, rowNumber) -> toLedgerEntry(row), Integer.parseInt(courierId));
    }

    // The CHARGE entry of this order, written when it was delivered. null when it was never delivered.
    // Used by the order summary (#262): the summary's total is this entry's amount.
    public LedgerEntry findCharge(String orderId) {
        String sql = "SELECT * FROM ledger_entries WHERE order_id = ? AND entry_type = 'CHARGE'";
        List<LedgerEntry> found = jdbc.query(sql, (row, rowNumber) -> toLedgerEntry(row), Integer.parseInt(orderId));
        if (found.isEmpty()) {
            return null;
        }
        return found.get(0);
    }

    // Turns one row of the ledger_entries table into a LedgerEntry.
    private LedgerEntry toLedgerEntry(ResultSet row) throws SQLException {
        return new LedgerEntry(
                row.getString("order_id"),
                LedgerEntry.Type.valueOf(row.getString("entry_type")),
                OrderStatus.valueOf(row.getString("final_status")),
                row.getLong("subtotal"),
                row.getLong("delivery_fee"),
                row.getLong("service_fee"),
                row.getLong("surcharge"),
                row.getLong("amount"),
                row.getTimestamp("placed_at").toLocalDateTime(),
                row.getTimestamp("finished_at").toLocalDateTime(),
                row.getString("payment_reference"));
    }
}
