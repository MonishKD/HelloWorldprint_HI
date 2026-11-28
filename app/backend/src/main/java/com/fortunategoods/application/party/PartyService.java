package com.fortunategoods.application.party;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fortunategoods.application.ord.orders.OrderService;
import com.fortunategoods.application.ord.orders.OrderServiceResult;
import com.fortunategoods.domain.handlers.ItemAvailabilityHandler;
import com.fortunategoods.domain.handlers.RestaurantStatusHandler;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.order.validation.OrderRequest;
import com.fortunategoods.domain.order.validation.OrderVerify;
import com.fortunategoods.domain.order.validation.RequestedOrderLineItem;
import com.fortunategoods.domain.party.Party;
import com.fortunategoods.domain.party.PartyLine;
import com.fortunategoods.domain.party.PartyMember;
import com.fortunategoods.domain.party.PartyStatus;
import com.fortunategoods.domain.party.PartyVote;
import com.fortunategoods.domain.pricing.Fare;
import com.fortunategoods.domain.pricing.FareCalculator;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.technical.payment.Payment;
import com.fortunategoods.technical.payment.PaymentGateway;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.technical.persistence.OrderQueryRepository;
import com.fortunategoods.technical.persistence.PartyRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;

import jakarta.transaction.Transactional;

/*
    Party mode (phase4 extra feature). The party commands call this, the way the order commands call OrderService.
      1. start:     the host turns their cart into a party (their cart becomes their own items)
      2. join:      friends join with the code, the share link or the QR code (they have to be signed in)
      3. setLine:   each member adds their own items from the host's restaurant
      4. setReady:  each member says they are ready and votes ORDINARY or ROULETTE
      5. lock:      the host locks once everyone is ready; this fixes each member's share
      6. checkout + pay: each member holds their share on their card, like the cart's checkout (#259)
      7. once the last share is paid, the order is placed under the host's name (OrderService.placeOrder)
    The money is taken on delivery (or released on cancel/reject) by PaymentService, for every member's hold.

    Who calls always comes from the session (customerId), never from the request.
    @Transactional methods lock the party's row first (PartyRepository.lockRow), so two people acting
    at the same time are handled one after the other.
*/
@Service
public class PartyService {

    private static final Logger log = LoggerFactory.getLogger(PartyService.class);

    // Same limit as OrderVerify, for one item across the whole party.
    private static final int MAX_QUANTITY_PER_ITEM = 20;

    private final PartyRepository partyRepo;
    private final RestaurantRepository restaurantRepo;
    private final OrderService orderService;
    private final PaymentGateway paymentGateway;
    private final FareCalculator fareCalculator;
    private final OrderQueryRepository orderQueryRepo; // read-only order lookups (R1), for the placed order's status
    private final SecureRandom random = new SecureRandom();

    public PartyService(PartyRepository partyRepo, RestaurantRepository restaurantRepo, OrderService orderService,
            PaymentGateway paymentGateway, FareCalculator fareCalculator, OrderQueryRepository orderQueryRepo) {
        this.partyRepo = partyRepo;
        this.restaurantRepo = restaurantRepo;
        this.orderService = orderService;
        this.paymentGateway = paymentGateway;
        this.fareCalculator = fareCalculator;
        this.orderQueryRepo = orderQueryRepo;
    }

    // ---------- Reading ----------

    // Anyone signed in as a customer with the code can see the party, so someone who opens the link sees who invited them.
    public PartyResponse getParty(String code) {
        return view(requireParty(code));
    }

    // The party the customer is in that isn't placed or cancelled yet, or null.
    public PartyResponse getMyParty(String customerId) {
        String code = partyRepo.findActiveCodeOf(customerId);
        return code == null ? null : getParty(code);
    }

    // ---------- Gathering ----------

    // 1. The host's cart becomes a party. The cart must be orderable right now (open restaurant, items on the menu).
    @Transactional
    public PartyResponse start(String hostId, String restaurantId, List<RequestedOrderLineItem> cartItems,
            String instructions, String deliveryAddress) {
        partyRepo.lockCustomer(hostId); // two starts (or a start and a join) at once wait for each other
        requireNotInAnotherParty(hostId);
        OrderVerify.requireId(restaurantId, "Restaurant ID");
        Restaurant restaurant = restaurantRepo.findById(restaurantId);
        requireOrderable(restaurant, hostId, cartItems);

        // A random code, tried again in the very rare case another party already has it.
        String code = null;
        String partyId = null;
        for (int attempt = 0; attempt < 10 && partyId == null; attempt++) {
            code = Party.newCode(random);
            partyId = partyRepo.create(code, restaurantId, hostId, trimmed(deliveryAddress), trimmed(instructions));
        }
        if (partyId == null) {
            throw new IllegalStateException("Could not find a free party code");
        }

        partyRepo.addMember(partyId, hostId);
        for (RequestedOrderLineItem item : cartItems) {
            partyRepo.setLine(partyId, hostId, item.getMenuItemId(), item.getQuantity().intValueExact());
        }
        return getParty(code);
    }

    // 2. Joining twice changes nothing. Refused once the host locked the order.
    @Transactional
    public PartyResponse join(String customerId, String code) {
        partyRepo.lockCustomer(customerId); // joining two parties at once: the second waits, then sees the first
        Party party = lockAndLoad(code);
        if (!party.hasMember(customerId)) {
            party.requireStatus(PartyStatus.GATHERING, "This party already locked its order");
            requireNotInAnotherParty(customerId);
            partyRepo.addMember(party.getId(), customerId);
        }
        return getParty(party.getCode());
    }

    // A member (not the host) leaves while the party is still gathering; their items go with them.
    @Transactional
    public PartyResponse leave(String customerId, String code) {
        Party party = lockAndLoad(code);
        party.requireMember(customerId);
        if (party.isHost(customerId)) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "The host can't leave. Cancel the party instead.");
        }
        party.requireStatus(PartyStatus.GATHERING, "The order is locked, you can't leave now");
        partyRepo.removeMember(party.getId(), customerId);
        return getParty(party.getCode());
    }

    // 3. Sets how many of this menu item the member wants (0 removes it). Changing your items makes you not ready
    // again, so the host never locks an order someone is still changing.
    @Transactional
    public PartyResponse setLine(String customerId, String code, String itemId, Integer quantity) {
        Party party = lockAndLoad(code);
        party.requireMember(customerId);
        party.requireStatus(PartyStatus.GATHERING, "The order is locked, items can't change anymore");
        OrderVerify.requireId(itemId, "Item ID");
        if (quantity == null || quantity < 0 || quantity > MAX_QUANTITY_PER_ITEM) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID,
                    "Quantity must be between 0 and " + MAX_QUANTITY_PER_ITEM);
        }

        if (quantity > 0) {
            // How many of this item the whole party would have, so the merged order stays under the limit.
            int partyQuantity = quantity;
            for (PartyLine line : party.getLines()) {
                if (line.menuItemId().equals(itemId) && !line.memberId().equals(customerId)) {
                    partyQuantity += line.quantity();
                }
            }
            if (partyQuantity > MAX_QUANTITY_PER_ITEM) {
                throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID,
                        "The party can order at most " + MAX_QUANTITY_PER_ITEM + " of one item");
            }
            // The item must be on this restaurant's menu and available, and the restaurant open.
            Restaurant restaurant = restaurantRepo.findById(party.getRestaurantId());
            requireOrderable(restaurant, customerId, List.of(new RequestedOrderLineItem(itemId, BigDecimal.valueOf(quantity))));
        }

        partyRepo.setLine(party.getId(), customerId, itemId, quantity);
        partyRepo.setReady(party.getId(), customerId, false, null);
        return getParty(party.getCode());
    }

    // 4. Ready (with a vote) or not ready. You need at least one item to be ready.
    @Transactional
    public PartyResponse setReady(String customerId, String code, Boolean ready, String vote) {
        Party party = lockAndLoad(code);
        party.requireMember(customerId);
        party.requireStatus(PartyStatus.GATHERING, "The order is already locked");
        if (ready == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Say if you are ready (true or false)");
        }
        if (!ready) {
            partyRepo.setReady(party.getId(), customerId, false, null);
            return getParty(party.getCode());
        }

        if (party.linesOf(customerId).isEmpty()) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Add at least one item before you're ready");
        }
        requireLinesAvailable(party, restaurantRepo.findById(party.getRestaurantId()), customerId);
        partyRepo.setReady(party.getId(), customerId, true, parseVote(vote));
        return getParty(party.getCode());
    }

    // 5. The host locks: everyone must be ready. The roulette happens only if every member voted for it.
    // The fare and each share are fixed now, so what people pay can't move while they pay.
    @Transactional
    public PartyResponse lock(String customerId, String code) {
        Party party = lockAndLoad(code);
        party.requireHost(customerId);
        party.requireStatus(PartyStatus.GATHERING, "The order is already locked");
        party.requireEveryoneReady();

        Restaurant restaurant = restaurantRepo.findById(party.getRestaurantId());
        requireLinesAvailable(party, restaurant, null); // says whose item is sold out
        List<OrderLine> lines = requireOrderable(restaurant, customerId, party.mergedItems());
        Fare fare = fareCalculator.calculate(lines);

        PartyVote mode = party.chosenMode();
        String roulettePayerId = null;
        Map<String, Long> shares;
        if (mode == PartyVote.ROULETTE) {
            roulettePayerId = party.pickRoulettePayer(random);
            shares = party.rouletteShares(roulettePayerId, fare);
        } else {
            shares = party.ordinaryShares(priceCentsByItem(restaurant), fare);
        }
        Party.requireCardPayable(shares);

        if (!partyRepo.lock(party.getId(), mode, roulettePayerId, fare, shares, priceCentsByItem(restaurant))) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "The order is already locked");
        }
        return getParty(party.getCode());
    }

    // Cancelling, until the order is placed. Nothing is charged before that (card payments are only holds), so:
    //   gathering: only the host can cancel (anyone else can just leave)
    //   paying:    any member can call it off, so nobody is stuck if someone never pays
    // Every card hold started in the party is released, and everyone sees who cancelled.
    @Transactional
    public PartyResponse cancel(String customerId, String code) {
        Party party = lockAndLoad(code);
        party.requireMember(customerId);
        if (party.getStatus() == PartyStatus.GATHERING) {
            party.requireHost(customerId);
        } else {
            party.requireStatus(PartyStatus.PAYING, "This party is already over");
        }
        String reason = party.memberName(customerId) + (party.isHost(customerId) ? " (the host)" : "")
                + " cancelled the party. Nobody was charged.";
        if (!partyRepo.cancelIfNotPlaced(party.getId(), reason)) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "This party's order was already placed");
        }
        releaseAll(partyRepo.findStartedPaymentIds(party.getId()));
        return getParty(party.getCode());
    }

    // ---------- Paying ----------

    // 6a. Holds this member's share on their card and answers the client secret for the card field.
    // Starting again (the page was reloaded) releases the previous hold first.
    // @Transactional with the party's row locked, so two checkouts at once (two tabs) run one after the other
    // and the first hold is released instead of being forgotten.
    @Transactional
    public PartyCheckoutResponse checkout(String customerId, String code) {
        Party party = lockAndLoad(code);
        PartyMember member = party.requireMember(customerId);
        party.requireStatus(PartyStatus.PAYING, "The order is not ready to pay yet");
        if (member.paid()) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "You already paid your share");
        }
        if (member.shareCents() == 0) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "You have nothing to pay");
        }
        // Checked before any card is held: the restaurant still takes the order, at the locked prices.
        Restaurant restaurant = restaurantRepo.findById(party.getRestaurantId());
        List<OrderLine> lines = requireOrderable(restaurant, customerId, party.mergedItems());
        if (fareCalculator.calculate(lines).getTotalCents() != party.sharesTotalCents()) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT,
                    "The menu prices changed since the order was locked. Cancel the party and start again.");
        }

        if (member.paymentId() != null) {
            releaseAll(List.of(member.paymentId()));
        }
        Payment payment = paymentGateway.authorize(member.shareCents());
        partyRepo.savePaymentId(party.getId(), customerId, payment.getId());
        return new PartyCheckoutResponse(member.shareCents(), payment.getClientSecret());
    }

    // 6b. After the browser confirmed the card: the hold must be there for exactly the share.
    // 7.  When this was the last share, the order is placed under the host's name.
    // Not @Transactional on purpose: the payment is saved first, so if placing fails the holds can still be released.
    public PartyResponse pay(String customerId, String code) {
        Party party = requireParty(code);
        PartyMember member = party.requireMember(customerId);
        party.requireStatus(PartyStatus.PAYING, "The order is not ready to pay yet");
        if (member.paid()) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "You already paid your share");
        }
        if (member.shareCents() == 0) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "You have nothing to pay");
        }
        if (member.paymentId() == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Start the card payment first");
        }

        Payment payment = paymentGateway.find(member.paymentId());
        if (!payment.isHeld()) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "The card payment was not authorized");
        }
        if (payment.getAmountCents() != member.shareCents()) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "The card payment does not match your share");
        }
        if (!partyRepo.markPaid(party.getId(), customerId)) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Your share is already paid, or the party changed. Reload it.");
        }

        placeIfEveryonePaid(party.getId(), party.getCode());
        return getParty(party.getCode());
    }

    // Only the request that moves the party to PLACED places the order (claimForPlacement), so it is placed once.
    // If the order is refused (restaurant closed, item sold out, prices changed), every hold is released
    // and the party is cancelled, so nobody is charged for an order that doesn't exist.
    private void placeIfEveryonePaid(String partyId, String code) {
        if (!partyRepo.claimForPlacement(partyId)) {
            return;
        }
        Party party = requireParty(code);
        try {
            Restaurant restaurant = restaurantRepo.findById(party.getRestaurantId());
            List<OrderLine> lines = requireOrderable(restaurant, party.getHostId(), party.mergedItems());
            if (fareCalculator.calculate(lines).getTotalCents() != party.sharesTotalCents()) {
                throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "the menu prices changed since the order was locked");
            }
            OrderServiceResult placed = orderService.placeOrder(party.getHostId(), party.getRestaurantId(),
                    party.mergedItems(), party.getInstructions(), party.getDeliveryAddress());
            partyRepo.saveOrderId(partyId, placed.getOrderId());
        } catch (RuntimeException refused) {
            String reason = "Everyone paid, but the order could not be placed (" + refused.getMessage()
                    + "). Every card hold was released, nobody was charged.";
            partyRepo.markCancelled(partyId, reason);
            releaseAll(partyRepo.findStartedPaymentIds(partyId));
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, reason);
        }
    }

    // ---------- Helpers ----------

    // The party as the party room shows it: names and prices from the menu, the fare, and each member's share.
    private PartyResponse view(Party party) {
        Restaurant restaurant = restaurantRepo.findById(party.getRestaurantId());
        Map<String, MenuItem> menu = new HashMap<>();
        for (MenuItem item : restaurant.getMenu()) {
            menu.put(item.getId(), item);
        }

        List<PartyLineResponse> lines = new ArrayList<>();
        List<OrderLine> pricedLines = new ArrayList<>();
        for (PartyLine line : party.getLines()) {
            MenuItem item = menu.get(line.menuItemId());
            if (item == null) {
                lines.add(new PartyLineResponse(line.memberId(), line.menuItemId(), "No longer on the menu", 0, line.quantity(), false));
            } else {
                // After the lock, the price fixed then (what people pay), not today's menu price.
                long priceCents = line.lockedPriceCents() != null ? line.lockedPriceCents() : Math.round(item.getPrice() * 100);
                lines.add(new PartyLineResponse(line.memberId(), line.menuItemId(), item.getName(), priceCents, line.quantity(), item.isAvailable()));
                pricedLines.add(OrderLine.fromMenuItem(item, line.quantity()));
            }
        }

        // Locked: the fixed fare and shares. Gathering: a live estimate from the current menu.
        Fare fare = party.getLockedFare();
        Map<String, Long> estimate = null;
        if (fare == null) {
            fare = pricedLines.isEmpty() ? new Fare(0, 0, 0, 0) : fareCalculator.calculate(pricedLines);
            estimate = party.ordinaryShares(priceCentsByItem(restaurant), fare);
        }

        List<PartyMemberResponse> members = new ArrayList<>();
        for (PartyMember member : party.getMembers()) {
            long share = estimate == null ? member.shareCents() : estimate.getOrDefault(member.customerId(), 0L);
            members.add(new PartyMemberResponse(member.customerId(), member.name(), party.isHost(member.customerId()),
                    member.ready(), member.vote() == null ? null : member.vote().name(), share, member.paid()));
        }

        String orderStatus = null;
        if (party.getOrderId() != null) {
            Order order = orderQueryRepo.findById(party.getOrderId());
            orderStatus = order == null ? null : order.getStatus().name();
        }

        return new PartyResponse(party.getCode(), party.getRestaurantId(), restaurant.getName(), party.getHostId(),
                party.getDeliveryAddress(), party.getStatus().name(), party.getMode() == null ? null : party.getMode().name(),
                party.getRoulettePayerId(), party.getOrderId(), orderStatus, party.getCancelReason(), members, lines, fare.getSubtotalCents(),
                fare.getDeliveryFeeCents(), fare.getServiceFeeCents(), fare.getSurchargeCents(), fare.getTotalCents());
    }

    private Party requireParty(String code) {
        Party party = partyRepo.findByCode(Party.normalizeCode(code));
        if (party == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "No party with that code");
        }
        return party;
    }

    // Waits for other requests on this party to finish, then reads it (only inside a @Transactional method).
    private Party lockAndLoad(String code) {
        partyRepo.lockRow(Party.normalizeCode(code));
        return requireParty(code);
    }

    // One party at a time, so "my party" is never ambiguous.
    private void requireNotInAnotherParty(String customerId) {
        String activeCode = partyRepo.findActiveCodeOf(customerId);
        if (activeCode != null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT,
                    "You're already in party " + activeCode + ", one party at a time.");
        }
    }

    // The same checks as placing an order: items on the menu, restaurant open, items available (chain of responsibility).
    private List<OrderLine> requireOrderable(Restaurant restaurant, String customerId, List<RequestedOrderLineItem> items) {
        List<OrderLine> lines = OrderVerify.prepareLines(restaurant, items);
        RestaurantStatusHandler chain = new RestaurantStatusHandler();
        chain.setNextHandler(new ItemAvailabilityHandler());
        OrderVerify.validate(new OrderRequest(restaurant, customerId, null, lines), chain);
        return lines;
    }

    // Every item of this member (or of everyone when customerId is null) must still be on the menu and available.
    // The message names the item, and whose it is, so the right person removes it.
    private void requireLinesAvailable(Party party, Restaurant restaurant, String customerId) {
        Map<String, MenuItem> menu = new HashMap<>();
        for (MenuItem item : restaurant.getMenu()) {
            menu.put(item.getId(), item);
        }
        List<PartyLine> lines = customerId == null ? party.getLines() : party.linesOf(customerId);
        for (PartyLine line : lines) {
            MenuItem item = menu.get(line.menuItemId());
            if (item == null || !item.isAvailable()) {
                String what = item == null ? "item" : item.getName();
                String whose = customerId == null ? party.memberName(line.memberId()) + "'s " : "Your ";
                throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT,
                        whose + what + " is sold out or no longer on the menu. Remove it first.");
            }
        }
    }

    private Map<String, Long> priceCentsByItem(Restaurant restaurant) {
        Map<String, Long> prices = new HashMap<>();
        for (MenuItem item : restaurant.getMenu()) {
            prices.put(item.getId(), Math.round(item.getPrice() * 100));
        }
        return prices;
    }

    private PartyVote parseVote(String vote) {
        try {
            return PartyVote.valueOf(String.valueOf(vote).toUpperCase());
        } catch (IllegalArgumentException notAVote) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.INVALID, "Vote ORDINARY or ROULETTE");
        }
    }

    private static String trimmed(String text) {
        return text == null ? null : text.trim();
    }

    // Releasing must not hide the real error, so a failed release is only logged. A hold expires at Stripe after ~7 days.
    private void releaseAll(List<String> paymentIds) {
        for (String paymentId : paymentIds) {
            try {
                paymentGateway.cancel(paymentId);
            } catch (RuntimeException releaseError) {
                log.warn("Could not release party card payment {}: {}", paymentId, releaseError.getMessage());
            }
        }
    }
}
