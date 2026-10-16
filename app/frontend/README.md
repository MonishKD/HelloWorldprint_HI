# FortunateGoods frontend (v1)

Surplus-food delivery: restaurants sell unsold food at a discount, customers order it, couriers deliver it,
operators keep an eye on the whole platform.

This is the frontend only. All data is mock data held in memory, and the `*Api.ts` files are stubs that will be swapped
for `fetch()` calls to the real backend. Each stub has a TODO comment naming the endpoint it should call, and
`src/ui/adapters.ts` shows how the backend's JSON maps to the types the screens use. Where the Phase 1 and Phase 2
documents disagree, the frontend follows Phase 2.

## Run

```bash
cd app/frontend
npm install
npm run dev        # http://localhost:5173
npm run build      # type-check + production build
```

Data and the session live in memory, so a page reload resets everything and logs you out.
It needs Node.js 20.19 or newer. Light or dark mode follows your system by default, and the sun/moon button switches it and remembers your choice.

## Demo accounts

These are mock accounts for development and testing only, and the login page doesn't list them. Every account uses the password `demo123`, and you can log in with the email below or with the username (`alice`, `greenleaf`, `carl`, `operator`, and so on). They go away when the real backend and its seeded accounts replace the mock data.

| Role       | Email                | Notes                                         |
|------------|----------------------|-----------------------------------------------|
| Customer   | alice@demo.com       | Orders in every state                         |
| Customer   | bob@demo.com         | Has orders, one on its way                    |
| Restaurant | greenleaf@demo.com   | Green Leaf Bakery, open                       |
| Restaurant | sakura@demo.com      | Sakura Sushi, paused                          |
| Restaurant | tacoverde@demo.com   | Taco Verde, closed, everything sold out       |
| Courier    | courier@demo.com     | Carl, starts offline                          |
| Courier    | dina@demo.com        | Dina, mid-delivery                            |
| Courier    | eli@demo.com         | Eli, offline                                  |
| Operator   | operator@demo.com    | Platform overview                             |

## What's in v1

Auth: log in with a username or email, or register as a customer, restaurant or courier. A new restaurant starts closed.
The session is fake, each role is locked to its own dashboard, and the login page says when you were logged out or your session expired.

Customer: browse restaurants as a list or on a city map (search, cuisine filters, "open now"; click a pin to open its menu).
Menus show availability and savings. The cart takes a delivery address and special instructions, and asks for confirmation before
placing the order. The tracker follows an order through placed, accepted, preparing, ready, on the way and delivered, and a
customer can cancel while it is still "placed". Ordering is disabled when the restaurant is paused or closed, or when
everything is sold out. Once an order is ready or has a courier, a live map shows the route and the courier moving, with an ETA.

Restaurant: an open/paused/closed switch and a live board (New, Preparation queue, Ready for pickup, oldest first). Staff can
accept, reject with a reason, start preparing and mark ready. There is a menu editor (add, edit, remove, availability),
an empty state that walks a new restaurant through adding items and then opening, and an order history.

Courier: go online or offline, see delivery requests (orders being prepared or ready with no courier), and take several at
once, since one courier can carry many orders. Pickup is confirmed per order once it is ready, then marked delivered.
A map shows every active route plus nearby pickup requests. Earnings and history are there, but payouts are placeholders.

Operator: a city map (restaurants by status, couriers by state, active order addresses), platform stats, "needs attention"
alerts, all orders with filters and a cancel button, restaurant status control and a courier roster with live states.

Every dashboard has an output console with each action sent, the response (OK or error) and every error shown, timestamped
and collapsible. Every action that changes something asks for confirmation first.

## Where things live

```
src/
  domain.ts        TypeScript types for the domain model (User, Restaurant, Order, enums...) <- the contract
  mockData.ts      seed data, the in-memory "database" (MOCK ONLY, delete when the backend exists)
  session.tsx      fake session context + role -> dashboard path
  styles/          all CSS, split by area; tokens.css is the single colour palette (see below)
  ui/
    http.ts        fetch wrapper for the real backend: session cookie + CSRF, unwraps { data, message, eventId } (unused until the stubs are replaced)
    adapters.ts    converts backend JSON (UPPERCASE enums, latitude/longitude, empty fields) into the types in domain.ts
    common.tsx     shared UI: Logo, theme toggle, badges, confirm button, order card + tracker, stats, useLoad
    icons.tsx      line-icon set (no emoji)
    OrderLifecycle.tsx   animated order journey on the login screen
    reg/           LoginPage, RegisterPage, AuthShell, regApi            (login / register / logout / couriers)
    ord/           RestaurantListPage, MenuPage (customer browse + restaurant manage), OrderCart, IncomingOrdersList, ordApi   (restaurants / menus / orders)
    dashboard/     DashboardRouter (route guards + layout), Customer / Restaurant / Courier / Operator pages,
                   OutputConsolePanel, dashboardApi (console log, send() wrapper, preparation queue and customer order status data)
    map/           MapView (MapLibre engine), CityMap (R-ORD-01), mapStyle (reads --map-* tokens), geo (distance, ETA, routes)
```

Rules of thumb: pages never touch `mockData` directly, they call the `*Api.ts` functions; every user action goes through
`send()` so it shows up in the output console; types come from `domain.ts`.

## Colours and theming

All colours are defined once, in [`src/styles/tokens.css`](src/styles/tokens.css): a raw palette (teal, lime, coral,
sage/stone, forest), semantic tokens for light and dark (`--bg`, `--surface`, `--ink`, `--brand`, status pairs, ...),
the logo, the output console and the map (`--map-*`). Other CSS files and the map use `var(--token)` only, so a re-skin
is a one-file change. The theme is the `data-theme` attribute on `<html>`.

The logo is a single lockup (dark-green pill, white mark with a dark-green leaf, lime "Goods") that is identical on
every screen and in both themes.

## Maps

Maps use OpenStreetMap data as vector tiles from [OpenFreeMap](https://openfreemap.org) (no API key), drawn with
[MapLibre GL](https://maplibre.org) in the app's own palette (land, parks, water, buildings and roads only; no labels or
POIs). Street routes come from the public OSRM demo server. Both need internet, and without OSRM the route falls back to
a straight line. Once picked up, a delivery takes 3 minutes to cross the map in the demo (`SIM_DELIVERY_MS` in
`ui/map/geo.ts`). Seed coordinates are approximate. New customers get a random spot downtown. New restaurants get no coordinates at all, like the real sign-up, so they show in the list but not on the map until someone geocodes them.

No business rules live here; the backend will own validation and order state rules. The code that only exists because the
data is mocked is in `src/mockData.ts` and the `*Api.ts` stubs, and goes away when the backend is connected.
