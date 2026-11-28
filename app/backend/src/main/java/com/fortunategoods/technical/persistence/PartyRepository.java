package com.fortunategoods.technical.persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fortunategoods.domain.party.Party;
import com.fortunategoods.domain.party.PartyLine;
import com.fortunategoods.domain.party.PartyMember;
import com.fortunategoods.domain.party.PartyShare;
import com.fortunategoods.domain.party.PartyStatus;
import com.fortunategoods.domain.party.PartyVote;
import com.fortunategoods.domain.pricing.Fare;

/*
    Saves and reads parties (phase4 party mode) in parties, party_members and party_lines (schema/08-parties.sql).
    The updates that move a party forward only change a row still in the expected state ("... AND status = ?")
    and return whether they did, so two requests at the same time can't both win (like updateStatusIfUnchanged for orders).
*/
@Repository
public class PartyRepository {

    private final JdbcTemplate jdbc;

    public PartyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- Reading ----------

    // The whole party with its members and lines, or null when no party has this code.
    public Party findByCode(String code) {
        List<Party> found = jdbc.query("SELECT * FROM parties WHERE code = ?",
                (row, rowNumber) -> {
                    String partyId = row.getString("id");
                    Fare lockedFare = null;
                    if (row.getObject("subtotal_cents") != null) {
                        lockedFare = new Fare(row.getLong("subtotal_cents"), row.getLong("delivery_fee_cents"),
                                row.getLong("service_fee_cents"), row.getLong("surcharge_cents"));
                    }
                    String mode = row.getString("mode");
                    return new Party(partyId, row.getString("code"), row.getString("restaurant_id"),
                            row.getString("host_id"), row.getString("delivery_address"), row.getString("instructions"),
                            PartyStatus.valueOf(row.getString("status")), mode == null ? null : PartyVote.valueOf(mode),
                            row.getString("roulette_payer_id"), lockedFare, row.getString("order_id"),
                            row.getString("cancel_reason"), findMembers(partyId), findLines(partyId));
                }, code);
        return found.isEmpty() ? null : found.get(0);
    }

    // The code of the party this customer is in that isn't placed or cancelled yet, or null.
    public String findActiveCodeOf(String customerId) {
        List<String> found = jdbc.queryForList("SELECT parties.code FROM parties "
                + "JOIN party_members ON party_members.party_id = parties.id "
                + "WHERE party_members.customer_id = ? AND parties.status IN ('GATHERING', 'PAYING') "
                + "ORDER BY parties.id DESC LIMIT 1", String.class, Integer.parseInt(customerId));
        return found.isEmpty() ? null : found.get(0);
    }

    // Inside a transaction, makes other requests on the same party wait until this one is done (SELECT ... FOR UPDATE),
    // so for example a member can't add an item while the host is locking.
    public void lockRow(String code) {
        jdbc.queryForList("SELECT id FROM parties WHERE code = ? FOR UPDATE", code);
    }

    // Inside a transaction, makes this customer's other party requests wait (SELECT ... FOR UPDATE on their row),
    // so the same person can't start or join two parties at the same moment.
    public void lockCustomer(String customerId) {
        jdbc.queryForList("SELECT user_id FROM customers WHERE user_id = ? FOR UPDATE", Integer.parseInt(customerId));
    }

    // The card holds of the members of the party that placed this order (PaymentService captures, releases
    // or refunds them with the order). Empty for an order that didn't come from a party.
    public List<String> findPaymentIdsByOrder(String orderId) {
        return jdbc.queryForList("SELECT party_members.payment_intent_id FROM party_members "
                + "JOIN parties ON parties.id = party_members.party_id "
                + "WHERE parties.order_id = ? AND party_members.is_paid AND party_members.payment_intent_id IS NOT NULL",
                String.class, Integer.parseInt(orderId));
    }

    // The placed party orders this customer was in (as host or member), by order id, with what they paid and the code.
    // The history shows each member their own share of a party order (OrderHistoryService).
    public Map<String, PartyShare> findPartySharesOf(String customerId) {
        Map<String, PartyShare> shares = new HashMap<>();
        jdbc.query("SELECT parties.order_id, parties.code, party_members.share_cents FROM parties "
                + "JOIN party_members ON party_members.party_id = parties.id "
                + "WHERE party_members.customer_id = ? AND parties.order_id IS NOT NULL",
                row -> { shares.put(row.getString("order_id"), new PartyShare(row.getString("code"), row.getLong("share_cents"))); },
                Integer.parseInt(customerId));
        return shares;
    }

    // True when this customer was in the party that placed this order (so they may read its summary).
    public boolean isMemberOfOrder(String orderId, String customerId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM parties JOIN party_members ON party_members.party_id = parties.id "
                + "WHERE parties.order_id = ? AND party_members.customer_id = ?", Integer.class,
                Integer.parseInt(orderId), Integer.parseInt(customerId));
        return count != null && count > 0;
    }

    // Members in the order they joined (the host joins first). The name is the customer's name, or their username.
    private List<PartyMember> findMembers(String partyId) {
        return jdbc.query("SELECT party_members.*, COALESCE(customers.name, users.username) AS member_name "
                + "FROM party_members JOIN users ON users.id = party_members.customer_id "
                + "LEFT JOIN customers ON customers.user_id = party_members.customer_id "
                + "WHERE party_members.party_id = ? ORDER BY party_members.joined_at, party_members.customer_id",
                (row, rowNumber) -> {
                    String vote = row.getString("vote");
                    return new PartyMember(row.getString("customer_id"), row.getString("member_name"),
                            row.getBoolean("is_ready"), vote == null ? null : PartyVote.valueOf(vote),
                            row.getLong("share_cents"), row.getString("payment_intent_id"), row.getBoolean("is_paid"));
                }, Integer.parseInt(partyId));
    }

    private List<PartyLine> findLines(String partyId) {
        return jdbc.query("SELECT * FROM party_lines WHERE party_id = ? ORDER BY added_at, menu_item_id",
                (row, rowNumber) -> new PartyLine(row.getString("customer_id"), row.getString("menu_item_id"),
                        row.getInt("quantity"), row.getObject("price_cents") == null ? null : row.getLong("price_cents")),
                Integer.parseInt(partyId));
    }

    // ---------- Gathering ----------

    // Saves a new party and returns its id, or null if another party already has this code (PartyService tries another).
    // ON CONFLICT DO NOTHING instead of catching the error, because a failed INSERT would break the whole transaction.
    public String create(String code, String restaurantId, String hostId, String deliveryAddress, String instructions) {
        List<String> created = jdbc.queryForList("INSERT INTO parties (code, restaurant_id, host_id, delivery_address, "
                + "instructions, status) VALUES (?, ?, ?, ?, ?, 'GATHERING') ON CONFLICT (code) DO NOTHING RETURNING id",
                String.class, code, Integer.parseInt(restaurantId), Integer.parseInt(hostId), deliveryAddress, instructions);
        return created.isEmpty() ? null : created.get(0);
    }

    public void addMember(String partyId, String customerId) {
        jdbc.update("INSERT INTO party_members (party_id, customer_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                Integer.parseInt(partyId), Integer.parseInt(customerId));
    }

    // Sets how many of this item the member wants. 0 removes it from their items.
    public void setLine(String partyId, String customerId, String menuItemId, int quantity) {
        if (quantity == 0) {
            jdbc.update("DELETE FROM party_lines WHERE party_id = ? AND customer_id = ? AND menu_item_id = ?",
                    Integer.parseInt(partyId), Integer.parseInt(customerId), Integer.parseInt(menuItemId));
            return;
        }
        jdbc.update("INSERT INTO party_lines (party_id, customer_id, menu_item_id, quantity) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (party_id, customer_id, menu_item_id) DO UPDATE SET quantity = EXCLUDED.quantity",
                Integer.parseInt(partyId), Integer.parseInt(customerId), Integer.parseInt(menuItemId), quantity);
    }

    // ready with their vote, or not ready (vote cleared).
    public void setReady(String partyId, String customerId, boolean ready, PartyVote vote) {
        jdbc.update("UPDATE party_members SET is_ready = ?, vote = ? WHERE party_id = ? AND customer_id = ?",
                ready, vote == null ? null : vote.name(), Integer.parseInt(partyId), Integer.parseInt(customerId));
    }

    // ---------- Locking and paying ----------

    // GATHERING -> PAYING with the mode, the roulette payer (or null), the frozen fare, each member's share
    // and each item's price. Returns false if the party was no longer GATHERING.
    public boolean lock(String partyId, PartyVote mode, String roulettePayerId, Fare fare, Map<String, Long> shares,
            Map<String, Long> priceCentsByItem) {
        int changed = jdbc.update("UPDATE parties SET status = 'PAYING', mode = ?, roulette_payer_id = ?, "
                + "subtotal_cents = ?, delivery_fee_cents = ?, service_fee_cents = ?, surcharge_cents = ? "
                + "WHERE id = ? AND status = 'GATHERING'", mode.name(),
                roulettePayerId == null ? null : Integer.parseInt(roulettePayerId), fare.getSubtotalCents(),
                fare.getDeliveryFeeCents(), fare.getServiceFeeCents(), fare.getSurchargeCents(), Integer.parseInt(partyId));
        if (changed != 1) {
            return false;
        }
        for (Map.Entry<String, Long> share : shares.entrySet()) {
            jdbc.update("UPDATE party_members SET share_cents = ? WHERE party_id = ? AND customer_id = ?",
                    share.getValue(), Integer.parseInt(partyId), Integer.parseInt(share.getKey()));
        }
        for (Map.Entry<String, Long> price : priceCentsByItem.entrySet()) {
            jdbc.update("UPDATE party_lines SET price_cents = ? WHERE party_id = ? AND menu_item_id = ?",
                    price.getValue(), Integer.parseInt(partyId), Integer.parseInt(price.getKey()));
        }
        return true;
    }

    // Remembers the card hold started at checkout, so /pay can check it without the browser sending it.
    public void savePaymentId(String partyId, String customerId, String paymentId) {
        jdbc.update("UPDATE party_members SET payment_intent_id = ? WHERE party_id = ? AND customer_id = ?",
                paymentId, Integer.parseInt(partyId), Integer.parseInt(customerId));
    }

    // Marks the member paid, only if they weren't already and the party is still PAYING. Returns whether it did.
    public boolean markPaid(String partyId, String customerId) {
        int changed = jdbc.update("UPDATE party_members SET is_paid = TRUE WHERE party_id = ? AND customer_id = ? "
                + "AND NOT is_paid AND EXISTS (SELECT 1 FROM parties WHERE id = ? AND status = 'PAYING')",
                Integer.parseInt(partyId), Integer.parseInt(customerId), Integer.parseInt(partyId));
        return changed == 1;
    }

    // PAYING -> PLACED, only when every member with something to pay has paid. Only one request can get true,
    // so when the last two members pay at the same time, the order is still placed once.
    public boolean claimForPlacement(String partyId) {
        int changed = jdbc.update("UPDATE parties SET status = 'PLACED' WHERE id = ? AND status = 'PAYING' "
                + "AND NOT EXISTS (SELECT 1 FROM party_members WHERE party_id = ? AND share_cents > 0 AND NOT is_paid)",
                Integer.parseInt(partyId), Integer.parseInt(partyId));
        return changed == 1;
    }

    public void saveOrderId(String partyId, String orderId) {
        jdbc.update("UPDATE parties SET order_id = ? WHERE id = ?", Integer.parseInt(orderId), Integer.parseInt(partyId));
    }

    // Cancels a party whose order is not placed yet, with the reason everyone sees. Returns whether it cancelled.
    public boolean cancelIfNotPlaced(String partyId, String reason) {
        int changed = jdbc.update("UPDATE parties SET status = 'CANCELLED', cancel_reason = ? WHERE id = ? "
                + "AND status IN ('GATHERING', 'PAYING')", reason, Integer.parseInt(partyId));
        return changed == 1;
    }

    // When everyone paid but the order could not be placed (restaurant closed, item sold out...).
    public void markCancelled(String partyId, String reason) {
        jdbc.update("UPDATE parties SET status = 'CANCELLED', cancel_reason = ? WHERE id = ?", reason, Integer.parseInt(partyId));
    }

    // A member leaves while the party is still gathering: their items go with them.
    public void removeMember(String partyId, String customerId) {
        jdbc.update("DELETE FROM party_lines WHERE party_id = ? AND customer_id = ?", Integer.parseInt(partyId), Integer.parseInt(customerId));
        jdbc.update("DELETE FROM party_members WHERE party_id = ? AND customer_id = ?", Integer.parseInt(partyId), Integer.parseInt(customerId));
    }

    // Every card hold started in this party (paid or only started), to release them when it is cancelled.
    public List<String> findStartedPaymentIds(String partyId) {
        return new ArrayList<>(jdbc.queryForList("SELECT payment_intent_id FROM party_members "
                + "WHERE party_id = ? AND payment_intent_id IS NOT NULL", String.class, Integer.parseInt(partyId)));
    }
}
