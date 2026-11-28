package com.fortunategoods.domain.party;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.validation.RequestedOrderLineItem;
import com.fortunategoods.domain.pricing.Fare;

/*
    A party (phase4 party mode): friends order together from the host's restaurant.
    Each member adds their own items, says they are ready and votes ORDINARY or ROULETTE.
    When everyone is ready the host locks it, each member pays their share, and once everyone paid
    the order is placed under the host's name.

    This class holds the party's rules (who can do what, and how much each member pays).
    PartyService loads it from PartyRepository, asks it, then saves the result.
*/
public class Party {

    // Letters and digits that can't be mixed up when read out loud (no 0/O or 1/I/L).
    private static final String CODE_CHARACTERS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    public static final int CODE_LENGTH = 6;

    // Stripe refuses a card payment under 50 cents (CAD), so no share can be smaller than that.
    public static final long SMALLEST_CARD_PAYMENT_CENTS = 50;

    private final String id;
    private final String code;
    private final String restaurantId;
    private final String hostId;
    private final String deliveryAddress;
    private final String instructions;
    private final PartyStatus status;
    private final PartyVote mode;
    private final String roulettePayerId;
    private final Fare lockedFare; // null until the host locks
    private final String orderId;
    private final String cancelReason; // null unless CANCELLED
    private final List<PartyMember> members; // in the order they joined, the host first
    private final List<PartyLine> lines;

    public Party(String id, String code, String restaurantId, String hostId, String deliveryAddress, String instructions,
            PartyStatus status, PartyVote mode, String roulettePayerId, Fare lockedFare, String orderId,
            String cancelReason, List<PartyMember> members, List<PartyLine> lines) {
        this.id = id;
        this.code = code;
        this.restaurantId = restaurantId;
        this.hostId = hostId;
        this.deliveryAddress = deliveryAddress;
        this.instructions = instructions;
        this.status = status;
        this.mode = mode;
        this.roulettePayerId = roulettePayerId;
        this.lockedFare = lockedFare;
        this.orderId = orderId;
        this.cancelReason = cancelReason;
        this.members = List.copyOf(members);
        this.lines = List.copyOf(lines);
    }

    // ---------- The code ----------

    // A new random code like "K7Q2MX". SecureRandom so codes can't be guessed from earlier ones.
    public static String newCode(SecureRandom random) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_CHARACTERS.charAt(random.nextInt(CODE_CHARACTERS.length())));
        }
        return code.toString();
    }

    // What people type or scan ("k7q-2mx") -> how it is saved ("K7Q2MX").
    public static String normalizeCode(String code) {
        if (code == null) {
            return "";
        }
        return code.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    // ---------- Checks: each one throws a DomainRuleViolation, so the console shows a clear refusal ----------

    public boolean isActive() {
        return status == PartyStatus.GATHERING || status == PartyStatus.PAYING;
    }

    public boolean isHost(String customerId) {
        return hostId.equals(customerId);
    }

    public boolean hasMember(String customerId) {
        return findMember(customerId) != null;
    }

    public PartyMember requireMember(String customerId) {
        PartyMember member = findMember(customerId);
        if (member == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN, "You're not in this party");
        }
        return member;
    }

    public void requireHost(String customerId) {
        if (!isHost(customerId)) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.FORBIDDEN, "Only the host can do that");
        }
    }

    // A party that is over says so, whatever the action was.
    public void requireStatus(PartyStatus expected, String message) {
        if (status == expected) {
            return;
        }
        if (status == PartyStatus.CANCELLED) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "This party was cancelled");
        }
        if (status == PartyStatus.PLACED) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "This party's order was already placed");
        }
        throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, message);
    }

    public String memberName(String customerId) {
        PartyMember member = findMember(customerId);
        return member == null ? "Someone" : member.name();
    }

    // The host can lock only when every member said they are ready (each one has at least one item then).
    public void requireEveryoneReady() {
        for (PartyMember member : members) {
            if (!member.ready()) {
                throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT,
                        "Not everyone is ready yet: waiting for " + member.name());
            }
        }
    }

    // The host can cancel until somebody paid.
    public boolean anyonePaid() {
        for (PartyMember member : members) {
            if (member.paid()) {
                return true;
            }
        }
        return false;
    }

    // ---------- What the party orders ----------

    public List<PartyLine> linesOf(String customerId) {
        List<PartyLine> own = new ArrayList<>();
        for (PartyLine line : lines) {
            if (line.memberId().equals(customerId)) {
                own.add(line);
            }
        }
        return own;
    }

    // Every member's items put together, one line per menu item (two people adding the same item add up).
    // This is what is priced and what the real order is placed with.
    public List<RequestedOrderLineItem> mergedItems() {
        Map<String, Integer> quantityByItem = new LinkedHashMap<>();
        for (PartyLine line : lines) {
            int soFar = quantityByItem.getOrDefault(line.menuItemId(), 0);
            quantityByItem.put(line.menuItemId(), soFar + line.quantity());
        }
        List<RequestedOrderLineItem> merged = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : quantityByItem.entrySet()) {
            merged.add(new RequestedOrderLineItem(entry.getKey(), BigDecimal.valueOf(entry.getValue())));
        }
        return merged;
    }

    // ROULETTE only when there are at least two people and every one of them voted for it; otherwise ORDINARY.
    public PartyVote chosenMode() {
        if (members.size() < 2) {
            return PartyVote.ORDINARY;
        }
        for (PartyMember member : members) {
            if (member.vote() != PartyVote.ROULETTE) {
                return PartyVote.ORDINARY;
            }
        }
        return PartyVote.ROULETTE;
    }

    // ---------- Who pays what (all in cents, like Fare and Stripe) ----------

    /*
        ORDINARY: each member pays the items they added, plus an even part of the fees (delivery, service, surcharge).
        Members with no items pay nothing. The cent or two left over by dividing the fees goes to the host
        (or to the first member with items if the host has none), so the shares always add up to the total.
        priceCentsByItem is each menu item's price in cents.
    */
    public Map<String, Long> ordinaryShares(Map<String, Long> priceCentsByItem, Fare fare) {
        Map<String, Long> shares = new LinkedHashMap<>();
        List<String> payers = new ArrayList<>();
        for (PartyMember member : members) {
            long itemsCents = 0;
            for (PartyLine line : linesOf(member.customerId())) {
                itemsCents += priceCentsByItem.getOrDefault(line.menuItemId(), 0L) * line.quantity();
            }
            shares.put(member.customerId(), itemsCents);
            if (itemsCents > 0) {
                payers.add(member.customerId());
            }
        }
        if (payers.isEmpty()) {
            return shares;
        }

        long feesCents = fare.getTotalCents() - fare.getSubtotalCents();
        long feePerPayer = feesCents / payers.size();
        long sharesTotal = 0;
        for (String payer : payers) {
            shares.put(payer, shares.get(payer) + feePerPayer);
        }
        for (long share : shares.values()) {
            sharesTotal += share;
        }
        String takesLeftover = payers.contains(hostId) ? hostId : payers.get(0);
        shares.put(takesLeftover, shares.get(takesLeftover) + fare.getTotalCents() - sharesTotal);
        return shares;
    }

    // ROULETTE: the picked member pays the whole order, everyone else nothing.
    public Map<String, Long> rouletteShares(String payerId, Fare fare) {
        Map<String, Long> shares = new LinkedHashMap<>();
        for (PartyMember member : members) {
            shares.put(member.customerId(), member.customerId().equals(payerId) ? fare.getTotalCents() : 0L);
        }
        return shares;
    }

    // A random member for the roulette.
    public String pickRoulettePayer(SecureRandom random) {
        return members.get(random.nextInt(members.size())).customerId();
    }

    // Every share above 0 must be big enough for a card payment.
    public static void requireCardPayable(Map<String, Long> shares) {
        for (long share : shares.values()) {
            if (share > 0 && share < SMALLEST_CARD_PAYMENT_CENTS) {
                throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT,
                        "A share can't be under $0.50, the smallest card payment. Add an item or play the roulette.");
            }
        }
    }

    // The sum of what everyone pays, compared with the order's fare before it is placed.
    public long sharesTotalCents() {
        long total = 0;
        for (PartyMember member : members) {
            total += member.shareCents();
        }
        return total;
    }

    // ---------- Getters ----------

    private PartyMember findMember(String customerId) {
        for (PartyMember member : members) {
            if (member.customerId().equals(customerId)) {
                return member;
            }
        }
        return null;
    }

    public String getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getRestaurantId() {
        return restaurantId;
    }

    public String getHostId() {
        return hostId;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public String getInstructions() {
        return instructions;
    }

    public PartyStatus getStatus() {
        return status;
    }

    public PartyVote getMode() {
        return mode;
    }

    public String getRoulettePayerId() {
        return roulettePayerId;
    }

    public Fare getLockedFare() {
        return lockedFare;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public List<PartyMember> getMembers() {
        return members;
    }

    public List<PartyLine> getLines() {
        return lines;
    }
}
