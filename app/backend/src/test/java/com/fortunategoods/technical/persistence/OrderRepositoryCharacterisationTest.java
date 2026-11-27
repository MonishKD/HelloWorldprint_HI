package com.fortunategoods.technical.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.fortunategoods.domain.courier.CourierJob;
import com.fortunategoods.domain.courier.DispatchQueue;
import com.fortunategoods.domain.courier.ReadyOrderSummary;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.technical.persistence.OrderEventRepository;
import com.fortunategoods.technical.persistence.OrderRepository;

/**
 * Characterisation tests that focus on OrderRepository read behaviour (R1, Extract Class).
 *
 * They pin what the code does TODAY and must pass unchanged on both the test(R1)
 * and refactor(R1) commits. They only use OrderRepository's public methods, so
 * delegation to OrderQueryRepository cannot break them.
 *
 * Database-backed: each test rolls back (@Transactional), so no cleanup is needed.
 *
 * Domain facts these tests rely on (from Order.java):
 *  - Hydrating an Order needs at least one line, and every line's MenuItem must belong
 *    to the order's restaurant, otherwise the Order constructor throws.
 *    So insertOrder() always adds one default line; insertOrderBare() adds none.
 *  - updateStatus() hydrates through findById, so status moves in these tests use
 *    updateStatusIfUnchanged(), which does not hydrate.
 *
 * Seed helpers follow schema files 01 to 04. Remaining assumption: a Postgres test DB
 * reachable through @SpringBootTest (swap the annotations if your other repository tests differ).
 * 
 * Testing in terminal shows all 23 tests ran with 0 failures and errors!
 */
@SpringBootTest
@Transactional
class OrderRepositoryCharacterisationTest {

    @Autowired OrderRepository orders;
    @Autowired OrderEventRepository events;
    @Autowired JdbcTemplate jdbc;

    private int customerA;
    private int customerB;
    private int restaurantX;
    private int restaurantY;

    @BeforeEach
    void seed() {
        customerA = insertCustomer("Alice");
        customerB = insertCustomer("Bob");
        restaurantX = insertRestaurant("Resto X", "1 X Street", 45.50, -73.56);
        restaurantY = insertRestaurant("Resto Y", "2 Y Street", 45.51, -73.57);
        insertMenuItem(restaurantX, "Burger");
        insertMenuItem(restaurantY, "Noodles");
    }

    // findById tests

    @Test
    void findById_found_hydratesAllFieldsAndLines() {
        int id = insertOrderBare(customerA, restaurantX, OrderStatus.PLACED, "no onions", "10 Main St", 45.5, -73.6, null);
        insertLine(id, "Burger", 2, "12.99", "15.99");
        insertLine(id, "Fries", 1, "4.50", null);

        Order o = orders.findById(String.valueOf(id));

        assertNotNull(o);
        assertEquals(String.valueOf(id), o.getId());
        assertEquals(String.valueOf(customerA), o.getCustomerId());
        assertEquals(String.valueOf(restaurantX), o.getRestaurantId());
        assertEquals(OrderStatus.PLACED, o.getStatus());
        assertEquals("no onions", o.getSpecialInstructions());
        assertEquals("10 Main St", o.getDeliveryAddress());
        assertEquals(45.5, o.getDeliveryLatitude());
        assertEquals(-73.6, o.getDeliveryLongitude());
        assertNull(o.getCourierId());
        assertNull(o.getRejectReason());

        List<OrderLine> lines = o.getLines();
        assertEquals(2, lines.size());
        assertEquals("Burger", lines.get(0).getName());          // ordered by line id
        assertEquals(2, lines.get(0).getQuantity());
        assertEquals(12.99, lines.get(0).getPrice());             // decimal-price regression
        assertEquals(15.99, lines.get(0).getOriginalPrice());
        assertEquals("Fries", lines.get(1).getName());
        assertEquals(4.50, lines.get(1).getPrice());
        assertNull(lines.get(1).getOriginalPrice());              // must stay null, not 0.0
    }

    @Test
    void findById_missing_returnsNull() {
        assertNull(orders.findById("999999"));
    }

    @Test
    void findById_orderWithoutLines_failsDomainValidation() {
        // Pins current behaviour: the Order constructor rejects an order with no lines,
        // so hydration throws instead of returning an order with an empty line list.
        int id = insertOrderBare(customerA, restaurantX, OrderStatus.PLACED, null, null, null, null, null);
        assertThrows(DomainRuleViolation.class, () -> orders.findById(String.valueOf(id)));
    }

    @Test
    void findById_rejectedOrder_exposesRejectReason() {
        int id = insertOrder(customerA, restaurantX, OrderStatus.REJECTED, null, null, null, null, "Out of stock");
        assertEquals("Out of stock", orders.findById(String.valueOf(id)).getRejectReason());
    }

    // customer / restaurant lists and ordering tests

    @Test
    void findByCustomer_isOldestFirst_andFiltersByCustomer() {
        int first = insertOrder(customerA, restaurantX, OrderStatus.PLACED, null, null, null, null, null);
        insertOrder(customerB, restaurantX, OrderStatus.PLACED, null, null, null, null, null);
        int third = insertOrder(customerA, restaurantY, OrderStatus.CANCELLED, null, null, null, null, null);

        assertEquals(List.of(String.valueOf(first), String.valueOf(third)), ids(orders.findByCustomer(String.valueOf(customerA))));
    }

    @Test
    void findByCustomerId_isNewestFirst_andFiltersByCustomer() {
        int first = insertOrder(customerA, restaurantX, OrderStatus.PLACED, null, null, null, null, null);
        insertOrder(customerB, restaurantX, OrderStatus.PLACED, null, null, null, null, null);
        int third = insertOrder(customerA, restaurantY, OrderStatus.CANCELLED, null, null, null, null, null);

        assertEquals(List.of(String.valueOf(third), String.valueOf(first)), ids(orders.findByCustomerId(String.valueOf(customerA))));
    }

    @Test
    void findByCustomer_andFindByCustomerId_noOrders_returnEmpty() {
        assertTrue(orders.findByCustomer(String.valueOf(customerA)).isEmpty());
        assertTrue(orders.findByCustomerId(String.valueOf(customerA)).isEmpty());
    }

    @Test
    void findByRestaurant_isOldestFirst_whateverStatus() {
        int a = insertOrder(customerA, restaurantX, OrderStatus.PLACED, null, null, null, null, null);
        insertOrder(customerA, restaurantY, OrderStatus.PLACED, null, null, null, null, null);
        int c = insertOrder(customerB, restaurantX, OrderStatus.DELIVERED, null, null, null, null, null);

        assertEquals(List.of(String.valueOf(a), String.valueOf(c)), ids(orders.findByRestaurant(String.valueOf(restaurantX))));
        assertTrue(orders.findByRestaurant("999999").isEmpty());
    }

    @Test
    void findAll_isOldestFirst_includesEveryStatus() {
        int a = insertOrder(customerA, restaurantX, OrderStatus.PLACED, null, null, null, null, null);
        int b = insertOrder(customerB, restaurantY, OrderStatus.REJECTED, null, null, null, null, "no");
        List<String> all = ids(orders.findAll());
        assertEquals(List.of(String.valueOf(a), String.valueOf(b)), all.subList(all.size() - 2, all.size()));
    }

    // tests that focus restaurant + status, ordered by arrival event (related to R-ORD-03) 

    @Test
    void findByRestaurantAndStatus_ordersByTimeOfEventThatMovedOrderToStatus() {
        int first = placeWithEvent(customerA, restaurantX);
        int second = placeWithEvent(customerB, restaurantX);
        int otherRestaurant = placeWithEvent(customerA, restaurantY);

        // the second order reaches ACCEPTED before the first one does
        move(second, OrderStatus.PLACED, OrderStatus.ACCEPTED);
        move(first, OrderStatus.PLACED, OrderStatus.ACCEPTED);
        move(otherRestaurant, OrderStatus.PLACED, OrderStatus.ACCEPTED);

        List<Order> accepted = orders.findByRestaurantAndStatus(String.valueOf(restaurantX), OrderStatus.ACCEPTED);
        assertEquals(List.of(String.valueOf(second), String.valueOf(first)), ids(accepted));
    }

    @Test
    void findByRestaurantAndStatus_noMatch_returnsEmpty() {
        placeWithEvent(customerA, restaurantX);
        assertTrue(orders.findByRestaurantAndStatus(String.valueOf(restaurantX), OrderStatus.READY).isEmpty());
    }

    // ---------- moved display queries ----------

    @Test
    void orderCount_countsOnlyAcceptedPreparingReady_andOmitsEmptyRestaurants() {
        // No hydration here, so orders without lines are fine.
        insertOrderBare(customerA, restaurantX, OrderStatus.ACCEPTED, null, null, null, null, null);
        insertOrderBare(customerA, restaurantX, OrderStatus.ACCEPTED, null, null, null, null, null);
        insertOrderBare(customerA, restaurantX, OrderStatus.READY, null, null, null, null, null);
        insertOrderBare(customerA, restaurantX, OrderStatus.PLACED, null, null, null, null, null);     // ignored
        insertOrderBare(customerA, restaurantY, OrderStatus.DELIVERED, null, null, null, null, null);  // ignored

        Map<String, Map<OrderStatus, Integer>> counts = orders.orderCount();

        Map<OrderStatus, Integer> x = counts.get(String.valueOf(restaurantX));
        assertEquals(2, x.get(OrderStatus.ACCEPTED));
        assertEquals(1, x.get(OrderStatus.READY));
        assertNull(x.get(OrderStatus.PREPARING));                 // absent, not zero
        assertNull(x.get(OrderStatus.PLACED));
        assertFalse(counts.containsKey(String.valueOf(restaurantY)));
    }

    @Test
    void findReadyOrdersForCourier_onlyReady_withItemCountAndZeroDefault() {
        // The summary query does not hydrate Orders, so bare orders are fine.
        int withLines = placeBareWithEvent(customerA, restaurantX);
        insertLine(withLines, "Burger", 2, "10.00", null);
        insertLine(withLines, "Fries", 3, "4.00", null);
        int noLines = placeBareWithEvent(customerB, restaurantX);
        int notReady = placeBareWithEvent(customerA, restaurantY);
        makeReady(withLines);
        makeReady(noLines);
        move(notReady, OrderStatus.PLACED, OrderStatus.ACCEPTED);

        List<ReadyOrderSummary> ready = orders.findReadyOrdersForCourier();

        assertEquals(2, ready.size());                            // oldest ready first
        ReadyOrderSummary s0 = ready.get(0);
        assertEquals(String.valueOf(withLines), s0.orderId());
        assertEquals("Resto X", s0.restaurantName());
        assertEquals("1 X Street", s0.pickupAddress());
        assertEquals(5, s0.itemCount());
        assertNotNull(s0.readyAt());
        assertEquals(0, ready.get(1).itemCount());                // COALESCE zero default
    }

    @Test
    void findWaitingForDispatch_readyWithoutLiveOffer_oldestFirst() {
        int a = placeWithEvent(customerA, restaurantX);
        int b = placeWithEvent(customerB, restaurantX);
        makeReady(a);
        makeReady(b);

        List<DispatchQueue.WaitingOrder> waiting = orders.findWaitingForDispatch();

        assertEquals(2, waiting.size());
        assertEquals(String.valueOf(a), waiting.get(0).order().getId());
        assertEquals(String.valueOf(b), waiting.get(1).order().getId());
        assertNotNull(waiting.get(0).readySince());
    }

    @Test
    void findWaitingForDispatch_excludesOrderWithLiveOffer() {
        int a = placeWithEvent(customerA, restaurantX);
        makeReady(a);
        insertOfferedAssignment(a);

        assertTrue(orders.findWaitingForDispatch().isEmpty());
    }

    @Test
    void findCourierJob_readyOrder_hydratesLinesAndNullablePickedUp() {
        int id = placeBareWithEvent(customerA, restaurantX);
        insertLine(id, "Burger", 2, "10.00", null);
        makeReady(id);

        CourierJob job = orders.findCourierJob(String.valueOf(id));

        assertNotNull(job);
        assertEquals(String.valueOf(id), job.orderId());
        assertEquals("READY", job.status());
        assertEquals("Resto X", job.restaurantName());
        assertEquals("1 X Street", job.pickupAddress());
        assertEquals(45.50, job.pickupLatitude());
        assertEquals(-73.56, job.pickupLongitude());
        assertEquals("Alice", job.destinationName());             // comes from customers.name
        assertEquals(1, job.lines().size());
        assertEquals("Burger", job.lines().get(0).name());
        assertEquals(2, job.lines().get(0).quantity());
        assertNull(job.pickedUpAt());
    }

    @Test
    void nullDeliveryCoordinates_stayNullInFindByIdAndFindCourierJob() {
        int id = placeWithEvent(customerA, restaurantX);
        makeReady(id);

        assertNull(orders.findById(String.valueOf(id)).getDeliveryLatitude());
        assertNull(orders.findById(String.valueOf(id)).getDeliveryLongitude());
        assertNull(orders.findCourierJob(String.valueOf(id)).deliveryLatitude());
        assertNull(orders.findCourierJob(String.valueOf(id)).instructions());
    }

    @Test
    void findCourierJob_missingOrNotReadyOrPickedUp_returnsNull() {
        int placed = placeWithEvent(customerA, restaurantX);
        assertNull(orders.findCourierJob(String.valueOf(placed)));
        assertNull(orders.findCourierJob("999999"));
    }

    @Test
    void findStalledOrders_returnsReadyOrderAcceptedByOfflineCourier() {
        int stalled = placeWithEvent(customerA, restaurantX);
        makeReady(stalled);
        insertAcceptedAssignment(stalled, insertCourier("Gone", "OFFLINE"));

        List<Order> found = orders.findStalledOrders();

        assertEquals(List.of(String.valueOf(stalled)), ids(found));
        assertNotNull(found.get(0).getCourierId());
    }

    @Test
    void findStalledOrders_ignoresOnlineCourierAndOrdersWithoutAssignment() {
        int online = placeWithEvent(customerA, restaurantX);
        makeReady(online);
        insertAcceptedAssignment(online, insertCourier("Here", "IDLE"));
        int unassigned = placeWithEvent(customerB, restaurantX);
        makeReady(unassigned);

        assertTrue(orders.findStalledOrders().isEmpty());
    }

    // tests that focus on behaviour that must not change (locking / conditional updates) 

    @Test
    void updateStatus_missingOrder_returnsNull() {
        assertNull(orders.updateStatus("999999", null, OrderStatus.ACCEPTED));
    }

    @Test
    void updateStatusIfUnchanged_wrongExpectedStatus_changesNothing() {
        int id = placeWithEvent(customerA, restaurantX);
        String result = orders.updateStatusIfUnchanged(String.valueOf(id), null, OrderStatus.READY, OrderStatus.CANCELLED);
        assertNull(result);
        assertEquals(OrderStatus.PLACED, orders.findById(String.valueOf(id)).getStatus());
    }

    @Test
    void rejectOrderIfPlaced_onlyWorksWhilePlaced_andStoresReason() {
        int placed = placeWithEvent(customerA, restaurantX);
        assertNotNull(orders.rejectOrderIfPlaced(String.valueOf(placed), lastEventId(placed), "Closed"));
        Order rejected = orders.findById(String.valueOf(placed));
        assertEquals(OrderStatus.REJECTED, rejected.getStatus());
        assertEquals("Closed", rejected.getRejectReason());

        assertNull(orders.rejectOrderIfPlaced(String.valueOf(placed), null, "Again"));
    }

    // helpers

    private static List<String> ids(List<Order> list) {
        return list.stream().map(Order::getId).collect(Collectors.toList());
    }

    /** PLACED order with one default line (hydratable) and its first event. */
    private int placeWithEvent(int customerId, int restaurantId) {
        int id = insertOrder(customerId, restaurantId, OrderStatus.PLACED, null, null, null, null, null);
        events.addFirst(String.valueOf(id));
        return id;
    }

    /** PLACED order with NO lines (not hydratable) and its first event. */
    private int placeBareWithEvent(int customerId, int restaurantId) {
        int id = insertOrderBare(customerId, restaurantId, OrderStatus.PLACED, null, null, null, null, null);
        events.addFirst(String.valueOf(id));
        return id;
    }

    private void makeReady(int orderId) {
        move(orderId, OrderStatus.PLACED, OrderStatus.ACCEPTED);
        move(orderId, OrderStatus.ACCEPTED, OrderStatus.PREPARING);
        move(orderId, OrderStatus.PREPARING, OrderStatus.READY);
    }

    /** Uses the existing public updateStatusIfUnchanged so event rows are written as in production, without hydrating. */
    private void move(int orderId, OrderStatus from, OrderStatus to) {
        String eventId = orders.updateStatusIfUnchanged(String.valueOf(orderId), lastEventId(orderId), from, to);
        assertNotNull(eventId, "test setup: status move " + from + " -> " + to + " did not apply");
    }

    private String lastEventId(int orderId) {
        List<String> ids = jdbc.queryForList("SELECT id FROM order_events WHERE order_id = ? ORDER BY id DESC LIMIT 1", String.class, orderId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** Order row plus one default line from the order's own restaurant, so it can be hydrated. */
    private int insertOrder(int customerId, int restaurantId, OrderStatus status, String instructions,
                            String address, Double lat, Double lon, String rejectReason) {
        int id = insertOrderBare(customerId, restaurantId, status, instructions, address, lat, lon, rejectReason);
        insertLine(id, "Default", 1, "1.00", null);
        return id;
    }

    private int insertOrderBare(int customerId, int restaurantId, OrderStatus status, String instructions,
                                String address, Double lat, Double lon, String rejectReason) {
        return jdbc.queryForObject(
            "INSERT INTO orders (customer_id, restaurant_id, status, special_instructions, delivery_address, "
          + "delivery_latitude, delivery_longitude, reject_reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
            Integer.class, customerId, restaurantId, status.name(), instructions, address, lat, lon, rejectReason);
    }

    /** Uses a menu item of the order's own restaurant (the Order constructor requires that). */
    private void insertLine(int orderId, String name, int qty, String price, String originalPrice) {
        int menuItemId = jdbc.queryForObject(
            "SELECT mi.id FROM menu_items mi JOIN menus m ON m.id = mi.menu_id "
          + "WHERE m.restaurant_id = (SELECT restaurant_id FROM orders WHERE id = ?) ORDER BY mi.id LIMIT 1",
            Integer.class, orderId);
        jdbc.update("INSERT INTO order_lines (order_id, menu_item_id, quantity, name, price, original_price) VALUES (?, ?, ?, ?, ?::numeric, ?::numeric)",
            orderId, menuItemId, qty, name, price, originalPrice);
    }

    // Seed helpers, written against schema files 01 to 04.
    private int insertUser(String username, String role) {
        return jdbc.queryForObject(
            "INSERT INTO users (username, email, password_hash, role) VALUES (?, ?, 'x', ?) RETURNING id",
            Integer.class, username, username + "@test.local", role);
    }
    private int insertCustomer(String name) {
        int userId = insertUser("cust_" + name.toLowerCase(), "CUSTOMER");
        jdbc.update("INSERT INTO customers (user_id, name) VALUES (?, ?)", userId, name);
        return userId;
    }
    private int insertCourier(String name, String status) {
        int userId = insertUser("courier_" + name.toLowerCase(), "COURIER");
        jdbc.update("INSERT INTO couriers (user_id, name, status) VALUES (?, ?, ?)", userId, name, status);
        return userId;
    }
    private int insertRestaurant(String name, String address, double lat, double lon) {
        int id = jdbc.queryForObject(
            "INSERT INTO restaurants (name, status, address, latitude, longitude) VALUES (?, 'OPEN', ?, ?, ?) RETURNING id",
            Integer.class, name, address, lat, lon);
        jdbc.update("INSERT INTO menus (restaurant_id) VALUES (?)", id);
        return id;
    }
    private int insertMenuItem(int restaurantId, String name) {
        return jdbc.queryForObject(
            "INSERT INTO menu_items (menu_id, name, price, status) "
          + "VALUES ((SELECT id FROM menus WHERE restaurant_id = ?), ?, 10.00, 'AVAILABLE') RETURNING id",
            Integer.class, restaurantId, name);
    }
    /** Assignment with status OFFERED (the column default), is_picked_up and is_delivered false. */
    private void insertOfferedAssignment(int orderId) {
        int courierId = insertCourier("Offered", "IDLE");
        jdbc.update("INSERT INTO assignments (order_id, courier_id) VALUES (?, ?)", orderId, courierId);
    }
    /** Accepted, not picked up, not delivered; also sets orders.courier_id like a real accept would. */
    private void insertAcceptedAssignment(int orderId, int courierId) {
        jdbc.update("INSERT INTO assignments (order_id, courier_id, status) VALUES (?, ?, 'ACCEPTED')", orderId, courierId);
        jdbc.update("UPDATE orders SET courier_id = ? WHERE id = ?", courierId, orderId);
    }
}