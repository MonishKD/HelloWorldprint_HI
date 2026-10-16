# Backend integration guide

The frontend runs on mock data today. To connect the real backend, edit two files (`src/ui/reg/regApi.ts` and
`src/ui/ord/ordApi.ts`) and delete `src/mockData.ts`. No component should need to change, as long as the functions below
keep their signatures and return the shapes in [`src/domain.ts`](../src/domain.ts).

## 1. How to swap a stub for a real call

1. Copy `.env.example` to `.env` and set `VITE_API_URL` (default `http://localhost:8080/api`).
2. In `regApi.ts` / `ordApi.ts`, replace the body of each function with a call to `request()` from
   [`src/ui/http.ts`](../src/ui/http.ts). Every stub has a `TODO` comment with the suggested endpoint.
3. Auth needs nothing from you. `request()` already sends Spring Security's session cookie (`credentials: 'include'`) and
   echoes its CSRF token (the `XSRF-TOKEN` cookie) back in the `X-XSRF-TOKEN` header on every non-GET request.
4. While developing, the backend has to allow CORS from `http://localhost:5173` with credentials.
5. A `401` on any non-auth request clears the UI session and sends the user back to the login page ("Your session expired").
6. `request()` unwraps the backend's `{ data, message, eventId }` envelope (`CommandSuccess`) and returns `data`. Plain bodies
   come back as they are. The envelope's `message` and `eventId` go to the output console.
7. Map the response through an adapter (next section), so the screens keep seeing the types in `domain.ts`.

```ts
// before (mock)
export async function listMenu(restaurantId: string): Promise<MenuItem[]> {
  return copy(db.menuItems.filter((m) => m.restaurantId === restaurantId));
}
// after (real)
export const listMenu = (restaurantId: string) => request<MenuItem[]>(`/restaurants/${restaurantId}/menu`);
```

Errors: the backend answers a refusal as `{ "status", "code", "message" }`, and `request()` throws an `ApiError` carrying
`message`, `status` and `code`. A server that can't be reached gives `ApiError("Cannot reach the server…", 0, "NETWORK")`.
Every UI action goes through `send()` in `dashboardApi.ts`, which logs the action and any error to the on-screen output
console (R-DASH-06) and then shows the message inline.

## 1b. Adapters: `src/ui/adapters.ts`

The backend and the UI name things differently. Instead of changing screens, convert at the boundary:

```ts
export const listMenu = (id: string) => request<unknown>(`/restaurants/${id}/menu`).then(toMenuItems);
export const getRestaurant = (id: string) => request<unknown>(`/restaurants/${id}`).then(toRestaurant);
```

| Backend | Frontend | Handled by |
|---|---|---|
| Enums in UPPERCASE (`OPEN`, `PICKED_UP`, `SOLD_OUT`) | lowercase (`open`, `picked_up`, `sold_out`) | every `to…` adapter; `toBackendEnum()` for request bodies |
| `latitude` / `longitude`, `deliveryLatitude` / `deliveryLongitude` | `lat` / `lng`, `deliveryLat` / `deliveryLng` | `toRestaurant`, `toOrder`, `toCourierLocations`, `toSessionUser` |
| Menu response is a `RestaurantMenu` object | `MenuItem[]` | `toMenuItems` (accepts an array, or an object with `items` / `menu`) |
| `originalPrice` empty unless discounted | always a number (= price when there is no discount) | `toMenuItem`, `toOrderLine` |
| Restaurant coordinates empty for new sign-ups; cuisine empty | optional `lat` / `lng`; cuisine `''` | the UI skips what it can't place (no pin, no route, no ETA) |
| Orders have no `total`, names or created time (yet) | `total`, `customerName`, `restaurantName`, `createdAt` | `toOrder` falls back (total = sum of lines); see the open questions below |
| Couriers without a position | not drawn | `toCourierLocations` drops them |

The adapters follow the backend's domain classes and SQL schema, because the controllers and DTOs don't exist yet. When the
DTOs are final, adjust `adapters.ts` and nothing else.

For the console feed (#140), `toLogEntry()` in `dashboardApi.ts` turns a `command_log` row into a console line (`SUCCESS` is
OK, `REFUSED` and `ERROR` are shown as errors). Load the feed with `request<CommandLogRow[]>(...)` and show it in
`OutputConsolePanel`.

## 2. Functions the UI calls

| Function (file) | Suggested endpoint | Returns | Used by |
|---|---|---|---|
| `login(usernameOrEmail, password)` (regApi) | `POST /auth/login` | `SessionUser` (the session itself is the cookie) | Login (one field accepts username or email) |
| `register({username,name,email,password,role,restaurantName?,address?})` (regApi) | `POST /auth/register` | `SessionUser` | Register (customer / restaurant / courier; never operator). A restaurant is created together with its owner account and starts `closed`. A taken username or email is a `409` |
| `logout(userId)` (regApi) | `POST /auth/logout` | `void` | Log out (goes through `send()`; the UI ends the session either way) |
| `listCouriers()` (regApi) | `GET /couriers` | `SessionUser[]` | Courier (own state), Operator |
| `setCourierOnline(id, online)` (regApi) | `PATCH /couriers/{id}/availability` | `SessionUser` | Courier toggle |
| `listRestaurants()` (ordApi) | `GET /restaurants` | `RestaurantSummary[]` (`Restaurant` + `itemCount`, `availableCount`, `bestDiscount`) | Customer home, Operator |
| `getRestaurant(id)` | `GET /restaurants/{id}` | `Restaurant` | Menu, Restaurant dashboard |
| `setRestaurantStatus(id, status)` | `PATCH /restaurants/{id}/status` | `Restaurant` | Restaurant, Operator. Opening a restaurant with no menu items must be refused (`409`): the UI shows the message in place and in the console |
| `listMenu(restaurantId)` | `GET /restaurants/{id}/menu` | `MenuItem[]` | Menu, Restaurant dashboard |
| `setMenuItemStatus(itemId, status)` | `PATCH /menu-items/{id}/status` | `MenuItem` | Restaurant dashboard (availability switch) |
| `addMenuItem(restaurantId, {name,description,originalPrice,price,status})` | `POST /restaurants/{restaurantId}/menu` | `MenuItem` | Menu editor. Refuse a missing name or a price not above zero (`400`) |
| `updateMenuItem(restaurantId, itemId, {...})` | `PATCH /restaurants/{restaurantId}/menu/{itemId}` | `MenuItem` | Menu editor (inline edit) |
| `removeMenuItem(restaurantId, itemId)` | `DELETE /restaurants/{restaurantId}/menu/{itemId}` | `void` | Menu editor. Hide the item but keep past orders intact (order lines keep their own name and price). The owner comes from the session, not the path |
| `placeOrder({customerId,customerName,deliveryAddress,deliveryLat,deliveryLng,restaurant,lines,specialInstructions})` | `POST /orders` | `Order` (status `placed`) | Cart |
| `getCustomerOrderStatuses(customerId)` (dashboardApi) | `GET /customers/{id}/orders` | `Order[]` | My orders, Customer home stats (a2: CustomerOrderStatusView) |
| `listRestaurantOrders(restaurantId)` | `GET /restaurants/{id}/orders` | `Order[]` | Restaurant board |
| `getPreparationQueue(restaurantId)` (dashboardApi) | `GET /restaurants/{id}/preparation-queue` | `Order[]` accepted + preparing, in FIFO order | Restaurant "Preparation queue" column (a2: PreparationQueueView, R-ORD-03) |
| `listCourierOrders(courierId)` | `GET /couriers/{id}/orders` | `Order[]` | Courier dashboard |
| `listDispatchQueue()` | `GET /dispatch-queue` | `Order[]` (preparing/ready, no courier) | Courier requests |
| `assignCourier(orderId, {id,name})` | `POST /orders/{id}/assignment` | `Order` | Courier "Accept delivery" |
| `listCourierLocations()` | `GET /couriers/locations` | `CourierLocation[]` (id, name, lat, lng, state) | City map (customer Discover shows non-offline ones, operator shows all) |
| `listAllOrders()` | `GET /orders` | `Order[]` | Operator |
| `updateOrderStatus(orderId, status, rejectReason?)` | `POST /orders/{id}/transitions` | `Order` | Accept / prepare / ready / reject / cancel / pick up / deliver |

The UI shows the preparation queue exactly as received, since ordering it by arrival time is the backend's job (R-ORD-03).
The other lists (new orders, ready orders) are sorted oldest-first in the browser, for display only.

## 3. Order lifecycle the UI expects

`placed → accepted → preparing → ready → picked_up → delivered`, with terminal branches `rejected` and `cancelled`
(see `OrderStatus` in `domain.ts` and the State section of the Phase 2 doc). `refunded` exists in the enum and the
domain model, but Phase 2 treats it as a future state, so no screen uses it.

The UI only offers legal buttons, but the backend has to enforce the transitions (R-ORD-04) and record each one with an
event id (R-ORD-05). Who triggers what in v1:

| Transition | Actor |
|---|---|
| placed → accepted / rejected (reason required) | Restaurant |
| placed → cancelled | Customer (only while `placed`), Operator |
| accepted → preparing → ready | Restaurant |
| ready → picked_up → delivered | Courier |

## 3b. Output console (R-DASH-06) and the confirm step

- Every action that changes something (order transitions, cancel, menu availability, store status, courier online or offline,
  accept delivery, place order) waits for a "Yes" in the UI before any `*Api` function is called.
- `send()` in `dashboardApi.ts` logs `SENT` when an action goes out. It then logs `OK` with the backend's message and the
  transition's event id (`Order #5 accepted · event #42`), or `ERR` with the refusal and its code (`… [409 CONFLICT]`).
  Errors the UI shows without calling the backend are logged too. If the backend adds a `CommandLog` feed
  (a2: OutputConsoleFeed), `OutputConsolePanel` can read that instead.
- The session is Spring Security's cookie plus the CSRF header (see section 1). `login` returns the user, and `SessionProvider`
  only remembers who is signed in. It also shows "You have been logged out" or "Your session expired" on the login page.

## 4. Mock-only code (delete or replace)

| Where | What it does now | What the backend should do |
|---|---|---|
| `src/mockData.ts` | In-memory seed data, resets on reload | PostgreSQL seed (R-REG-03) |
| `ordApi.placeOrder` | Builds the order id and computes `total` on the client | Backend assigns ids and computes totals/pricing |
| `ordApi.syncCourierState` | Derives courier state (idle/assigned/carrying) from their orders | Courier state machine in the domain layer |
| `ordApi.updateOrderStatus` | Sets `pickedUpAt` when status becomes `picked_up` | Persist the timestamp (`OrderEvent.timestamp`) |
| `regApi.register` / `roughLocation()` | New accounts get a random downtown location | Geocode the address (e.g. Nominatim) |
| `CourierDashboardPage.payout()` | `$3.50 + 10%` placeholder | Real delivery fee from the order |
| `ui/map/geo.ts` (`SIM_DELIVERY_MS`, `etaMinutes`, `deliveryProgress`) | Fakes the courier moving along the route over 3 minutes | Real courier position (poll or WebSocket) and a real ETA |
| `ui/map/geo.ts` `fetchRoute()` | Public OSRM demo server | Self-hosted routing, or return the polyline from the backend |
| `OrderCart.tsx` `blockedReason` | UI-only check that disables "Place order" | Backend rejects invalid orders (R-ORD-02: restaurant open, items available) |
| `OperatorDashboardPage` `STALE_MINUTES`, "Needs attention" | Computed in the browser | Backend can flag stalled orders |
| `RestaurantListPage` / `OperatorDashboardPage` `2.5 kg CO₂e` per meal | Display-only estimate | Optional |
| Session in `src/session.tsx` | In-memory, lost on reload | Session cookie from `/auth/login`; on page load the UI can call `GET /auth/me` to restore who is signed in (not wired yet) |
| `regApi.register` / `setRestaurantStatus` / menu functions | Mock validation (taken username, empty-menu opening, bad item) | The same rules, enforced by the backend |

## 4b. Fields the UI uses beyond the original domain model

The UI shows a few things the first domain model diagram didn't list. The backend's SQL schema (`app/backend/src/main/resources/schema`)
already has most of them as columns; the Java domain classes don't all have them yet. The frontend JSON names are the ones in
`src/domain.ts` (`lat`/`lng`, `deliveryLat`/`deliveryLng`); `src/ui/adapters.ts` maps from the backend's `latitude`/`longitude` style.

| Entity | Field | In the SQL schema? | Why the UI needs it |
|---|---|---|---|
| `MenuItem` | `originalPrice` (optional, only for discounted items) | `menu_items.original_price` | The discount badge ("−58%"), savings in the cart, "up to −X%" on restaurants. Without it the item sells at its normal price and shows a plain "Deal" tile |
| `MenuItem` | `description` (optional) | `menu_items.description` (not in the Java class yet) | Shown on the menu |
| `Restaurant` | `cuisine` (optional), `address` | `restaurants.cuisine`, `.address` (not in the Java class yet) | Cuisine filters, menu header, courier pickup address |
| `Restaurant` | `latitude`, `longitude` (optional) | `restaurants.latitude`, `.longitude` | Map pin. Sign-up doesn't ask for them, so a new restaurant has no pin until it is geocoded |
| `OrderLine` | `name`, `price`, `originalPrice` (optional) | `order_lines.*` | Price snapshot at order time; the total and the savings shown depend on it |
| `Order` | `deliveryAddress`, `deliveryLatitude`, `deliveryLongitude` | `orders.delivery_*` | Drop-off on the maps and courier cards. The cart pre-fills the customer's saved address and lets them change it per order |
| `Order` | `courierName` | Derived | From the courier assignment (`assignments` table) |
| `Order` | `pickedUpAt` | Derived | The timestamp of the `PICKED_UP` row in `order_events`; drives the live map |
| `Restaurant` | `rating`, `distanceKm`, `pickupWindow` | No | Extra card details; hidden when missing |
| `Restaurant` | `hue` | No | Accent colour; derived from the id when absent |

The counts on the restaurant list (`itemCount`, `availableCount`, `bestDiscount`) are derived from the
menu; return them from `GET /restaurants` or let the frontend compute them from `GET /restaurants/{id}/menu`.

Transition events (R-ORD-05): `updateOrderStatus` should return the order; the unique event id can be added to the
response and logged in the console if you want it visible.

## 4c. Open questions for the backend team

- Orders: the board and the tracker need a created time, the customer, restaurant and courier names, and a total on every
  order. The `orders` table has none of these, so the response will have to join them in. Until it does, the UI shows
  "Customer" and "Restaurant" and keeps the order the backend sends. The total can be left out, since the UI adds up the lines.
- Placing an order: `OrderRequest` takes whole `Restaurant` and `MenuItem` objects plus a `customerID`, but the issues say the
  customer comes from the session. Could the body be
  `{ restaurantId, lines: [{ menuItemId, quantity }], specialInstructions, deliveryAddress, deliveryLat, deliveryLng }`?
- Restaurant list: the new `RestaurantSummary` (on `phase2-monish`) has no cuisine, address or item counts, and the cuisine
  filter and the "3 left" badges need them. Either they go into the summary, or the frontend fetches every menu.
- Missing coordinates: `RestaurantRepository` reads them with `getDouble`, so a `NULL` becomes `0.0` and a new restaurant would
  get a pin off the coast of Africa. A nullable `Double` would let the UI skip the pin.
- Java classes and SQL: cuisine and address (restaurants) and description (menu items) are columns, but not fields on the
  Java classes yet.
- Menu response: `GET …/menu` returns a `RestaurantMenu`, and whatever is in it decides what `toMenuItems` reads.
- Geocoding: new restaurants and customers have no coordinates until someone geocodes the address.

## 5. Map data the backend needs to provide

Latitude/longitude on `Restaurant` (`lat`, `lng`), on each `Order` (`deliveryLat`, `deliveryLng`) and on each courier
(`GET /couriers/locations`), as in the domain model (`latPosition` / `longPosition`).

## 6. Alignment with the Phase 2 document (a2)

Where the Phase 1 and Phase 2 documents disagree, the frontend follows Phase 2.

| Topic | Decision |
|---|---|
| Courier and orders | Phase 1 assumed a courier handles one active order at a time. Phase 2 and the domain model (one courier to many orders) don't, so a courier can carry several orders at once. |
| `refunded` | Phase 2's State section lists it as a *future* state, so there is no Refund button in v1. |
| Map library | Phase 1 planned Leaflet; Phase 2 doesn't mention maps. The app uses MapLibre GL so the map can use the app's palette. |
| `regApi` | Register, login and logout (logout was added). |
| `dashboardApi` | Holds the console log and the data the dashboards show: `getCustomerOrderStatuses` and `getPreparationQueue`. |
| Confirm step | Every state-changing action waits for confirmation before it reaches an `*Api` function. |
| Preparation queue | Shown exactly as the backend returns it (FIFO is the backend's rule); the column is named "Preparation queue". |
| `MenuPage` | One page for both roles: customer browses and adds to cart, restaurant manages its own items (a restaurant only ever sees its own menu). |
| Operator | Seeded only, never offered on Register. |
| R-ORD-01 "Load and Display City" | Built as `CityMap`: customer Discover (restaurants matching the filters, your address, online couriers; click a restaurant to open its menu) and the operator dashboard (all restaurants by status, all couriers by state, addresses of active orders). Courier positions are static seed values until the backend provides them. |

Gaps still open (not built):

- Operator extras from the Phase 1 overview: reassigning stalled orders, billing information and restoring the initial
  system state. v1 only flags stalled orders and lets the operator cancel them and pause or reopen restaurants.
- Donations to shelters (product overview) and Stripe test payments (Phase 1) are not in this frontend.
- R-REG-01 says "four roles" while the `ui.reg` section leaves the operator out. The UI follows the second.
