# Backend integration guide

The frontend is finished against **mock data**. Connecting the real backend means editing **only two files**
(`src/ui/reg/regApi.ts`, `src/ui/ord/ordApi.ts`) and deleting `src/mockData.ts`. No component needs to change as long
as the functions below keep their signatures and return the shapes in [`src/domain.ts`](../src/domain.ts).

## 1. How to swap a stub for a real call

1. Copy `.env.example` to `.env` and set `VITE_API_URL` (default `http://localhost:8080/api`).
2. In `regApi.ts` / `ordApi.ts`, replace the body of each function with a call to `request()` from
   [`src/ui/http.ts`](../src/ui/http.ts). Every stub has a `TODO` comment with the suggested endpoint.
3. After login, call `setAuthToken(token)` so later requests send `Authorization: Bearer <token>`.
4. Make the backend allow CORS from `http://localhost:5173` while developing.

```ts
// before (mock)
export async function listMenu(restaurantId: string): Promise<MenuItem[]> {
  return copy(db.menuItems.filter((m) => m.restaurantId === restaurantId));
}
// after (real)
export const listMenu = (restaurantId: string) => request<MenuItem[]>(`/restaurants/${restaurantId}/menu`);
```

**Errors:** throw an `Error` with a human-readable message (`request()` does this from the response's `message` field).
Every UI action goes through `send()` in `dashboardApi.ts`, which logs the action and any error to the on-screen
output console (R-DASH-06), then shows the message inline.

## 2. Functions the UI calls

| Function (file) | Suggested endpoint | Returns | Used by |
|---|---|---|---|
| `login(email, password)` (regApi) | `POST /auth/login` | `SessionUser` (+ token) | Login |
| `register({name,email,password,role,restaurantName?,address?})` (regApi) | `POST /auth/register` | `SessionUser` | Register (customer / restaurant / courier; never operator) |
| `logout(userId)` (regApi) | `POST /auth/logout` | `void` | Log out (goes through `send()`; the UI ends the session either way) |
| `listCouriers()` (regApi) | `GET /couriers` | `SessionUser[]` | Courier (own state), Operator |
| `setCourierOnline(id, online)` (regApi) | `PATCH /couriers/{id}/availability` | `SessionUser` | Courier toggle |
| `listRestaurants()` (ordApi) | `GET /restaurants` | `RestaurantSummary[]` (`Restaurant` + `itemCount`, `availableCount`, `bestDiscount`) | Customer home, Operator |
| `getRestaurant(id)` | `GET /restaurants/{id}` | `Restaurant` | Menu, Restaurant dashboard |
| `setRestaurantStatus(id, status)` | `PATCH /restaurants/{id}/status` | `Restaurant` | Restaurant, Operator |
| `listMenu(restaurantId)` | `GET /restaurants/{id}/menu` | `MenuItem[]` | Menu, Restaurant dashboard |
| `setMenuItemStatus(itemId, status)` | `PATCH /menu-items/{id}/status` | `MenuItem` | Restaurant dashboard |
| `placeOrder({customerId,customerName,deliveryAddress,deliveryLat,deliveryLng,restaurant,lines,specialInstructions})` | `POST /orders` | `Order` (status `placed`) | Cart |
| `getCustomerOrderStatuses(customerId)` (dashboardApi) | `GET /customers/{id}/orders` | `Order[]` | My orders, Customer home stats (a2: CustomerOrderStatusView) |
| `listRestaurantOrders(restaurantId)` | `GET /restaurants/{id}/orders` | `Order[]` | Restaurant board |
| `getPreparationQueue(restaurantId)` (dashboardApi) | `GET /restaurants/{id}/preparation-queue` | `Order[]` accepted + preparing, **FIFO** | Restaurant "Preparation queue" column (a2: PreparationQueueView, R-ORD-03) |
| `listCourierOrders(courierId)` | `GET /couriers/{id}/orders` | `Order[]` | Courier dashboard |
| `listDispatchQueue()` | `GET /dispatch-queue` | `Order[]` (preparing/ready, no courier) | Courier requests |
| `assignCourier(orderId, {id,name})` | `POST /orders/{id}/assignment` | `Order` | Courier "Accept delivery" |
| `listCourierLocations()` | `GET /couriers/locations` | `CourierLocation[]` (id, name, lat, lng, state) | City map (customer Discover shows non-offline ones, operator shows all) |
| `listAllOrders()` | `GET /orders` | `Order[]` | Operator |
| `updateOrderStatus(orderId, status, rejectReason?)` | `POST /orders/{id}/transitions` | `Order` | Accept / prepare / ready / reject / cancel / pick up / deliver |

The UI shows the **preparation queue exactly as received**: ordering it by arrival time is the backend's job (R-ORD-03).
The other lists (new orders, ready orders) are sorted oldest-first in the browser for display only.

## 3. Order lifecycle the UI expects

`placed → accepted → preparing → ready → picked_up → delivered`, with terminal branches `rejected` and `cancelled`
(see `OrderStatus` in `domain.ts` and the State section of the Phase 2 doc). `refunded` exists in the enum and the
domain model, but Phase 2 treats it as a future state, so no screen uses it.

The UI only offers legal buttons, but **the backend must enforce transitions (R-ORD-04) and record each one with an
event id (R-ORD-05)**. Who triggers what in v1:

| Transition | Actor |
|---|---|
| placed → accepted / rejected (reason required) | Restaurant |
| placed → cancelled | Customer (only while `placed`), Operator |
| accepted → preparing → ready | Restaurant |
| ready → picked_up → delivered | Courier |

## 3b. Output console (R-DASH-06) and the confirm step

- **Confirm first:** every action that changes something (order transitions, cancel, menu availability, store status,
  courier online/offline, accept delivery, place order) waits for a "Yes" in the UI before any `*Api` function is called.
- **Console:** `send()` in `dashboardApi.ts` logs `SENT` when an action goes out, then `OK` or `ERR` with the backend's
  message (the response). Errors the UI shows without calling the backend are logged too. If the backend adds a
  `CommandLog` feed (a2: OutputConsoleFeed), `OutputConsolePanel` can read it instead.
- **Session:** `login` should return `{ ...user, token }`; `SessionProvider` passes the token to `setAuthToken()`.

## 4. Things that are MOCK ONLY (delete or replace)

| Where | What it does now | What the backend should do |
|---|---|---|
| `src/mockData.ts` | In-memory seed data, resets on reload | PostgreSQL seed (R-REG-03) |
| `ordApi.placeOrder` | Builds the order id and **computes `total` on the client** | Backend assigns ids and computes totals/pricing |
| `ordApi.syncCourierState` | Derives courier state (idle/assigned/carrying) from their orders | Courier state machine in the domain layer |
| `ordApi.updateOrderStatus` | Sets `pickedUpAt` when status becomes `picked_up` | Persist the timestamp (`OrderEvent.timestamp`) |
| `regApi.register` / `roughLocation()` | New accounts get a random downtown location | Geocode the address (e.g. Nominatim) |
| `CourierDashboardPage.payout()` | `$3.50 + 10%` placeholder | Real delivery fee from the order |
| `ui/map/geo.ts` (`SIM_DELIVERY_MS`, `etaMinutes`, `deliveryProgress`) | Fakes the courier moving along the route over 3 minutes | Real courier position (poll or WebSocket) and a real ETA |
| `ui/map/geo.ts` `fetchRoute()` | Public OSRM demo server | Self-hosted routing, or return the polyline from the backend |
| `OrderCart.tsx` `blockedReason` | UI-only check that disables "Place order" | Backend rejects invalid orders (R-ORD-02: restaurant open, items available) |
| `OperatorDashboardPage` `STALE_MINUTES`, "Needs attention" | Computed in the browser | Backend can flag stalled orders |
| `RestaurantListPage` / `OperatorDashboardPage` `2.5 kg CO₂e` per meal | Display-only estimate | Optional |
| Session in `src/session.tsx` | In-memory, lost on reload | Token from `/auth/login`; keep it in memory or a cookie |

## 4b. Fields the UI uses beyond the original domain model

The UI needs a few fields the first domain model didn't have. The **required** ones are now proposed in
`diagrams/domainModel.puml` (marked "proposed by frontend", for the team to review and accept). The frontend JSON names
are the ones in `src/domain.ts` (`lat`/`lng`, `deliveryLat`/`deliveryLng`); the backend can map them to the model's
`latPosition`/`longPosition` style.

| Entity | Field | Status | Why the UI needs it |
|---|---|---|---|
| `MenuItem` | `originalPrice` | **Required**, in the diagram | The discount badge ("−58%"), savings in the cart, "up to −X%" on restaurants: the "surplus food at a discount" idea |
| `MenuItem` | `description` | **Required**, in the diagram | Shown on the menu |
| `Restaurant` | `cuisine`, `address` | **Required**, in the diagram | Cuisine filters, menu header, courier pickup address |
| `OrderLine` | `name`, `price`, `originalPrice` | **Required**, in the diagram | Price snapshot at order time; `/totalCharges` and the savings shown depend on it |
| `Order` | `deliveryAddress`, `deliveryLat`, `deliveryLng` | **Required**, in the diagram | Drop-off on the maps and courier cards. The model keeps the saved address on `Customer`; the cart pre-fills it and lets the customer change it per order |
| `Order` | `courierName` | Derived | From the courier assignment (`Assignment` between Courier and Order) |
| `Order` | `pickedUpAt` | Derived | The timestamp of the `picked_up` entry in Order State Transitions (R-ORD-05); drives the live map |
| `Restaurant` | `rating`, `distanceKm`, `pickupWindow` | Optional | Extra card details; hidden when missing |
| `Restaurant` | `hue` | Optional | Accent colour; derived from the id when absent |

The counts on the restaurant list (`itemCount`, `availableCount`, `bestDiscount`) are derived from the
menu; return them from `GET /restaurants` or let the frontend compute them from `GET /restaurants/{id}/menu`.

Transition events (R-ORD-05): `updateOrderStatus` should return the order; the unique event id can be added to the
response and logged in the console if you want it visible.

## 5. Map data the backend needs to provide

Latitude/longitude on `Restaurant` (`lat`, `lng`), on each `Order` (`deliveryLat`, `deliveryLng`) and on each courier
(`GET /couriers/locations`), as in the domain model (`latPosition` / `longPosition`).

## 6. Alignment with the Phase 2 document (a2)

Where the Phase 1 and Phase 2 documents disagree, the frontend follows **Phase 2**.

| Topic | Decision |
|---|---|
| Courier and orders | Phase 1 assumed a courier handles one active order at a time. Phase 2 and the domain model (`Courier 1 — * Order`) don't, so a courier can carry several orders at once. |
| `refunded` | Phase 2's State section lists it as a *future* state, so there is no Refund button in v1. |
| Map library | Phase 1 planned Leaflet; Phase 2 doesn't mention maps. The app uses MapLibre GL so the map can use the app's palette. |
| `regApi` | Register, Login **and Logout** (added). |
| `dashboardApi` | Holds the console log **and** the data the dashboards show: `getCustomerOrderStatuses`, `getPreparationQueue`. |
| Confirm step | Every state-changing action waits for confirmation before it reaches an `*Api` function. |
| Preparation queue | Shown exactly as the backend returns it (FIFO is the backend's rule); the column is named "Preparation queue". |
| `MenuPage` | One page for both roles: customer browses and adds to cart, restaurant manages its own items (a restaurant only ever sees its own menu). |
| Operator | Seeded only, never offered on Register. |
| R-ORD-01 "Load and Display City" | Built as `CityMap`: customer Discover (restaurants matching the filters, your address, online couriers; click a restaurant to open its menu) and the operator dashboard (all restaurants by status, all couriers by state, addresses of active orders). Courier positions are static seed values until the backend provides them. |

Gaps still open (not built):

- **Operator extras from the Phase 1 overview**: reassigning stalled orders, billing information and restoring the
  initial system state. v1 only flags stalled orders and lets the operator cancel them and pause/reopen restaurants.
- **Donations to shelters** (product overview) and **Stripe test payments** (Phase 1) are not in this frontend.
- R-REG-01 says "four roles" while the `ui.reg` section excludes operator; the UI follows the second.
