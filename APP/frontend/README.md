# FortunateGoods — frontend (v1)

Surplus-food delivery: restaurants sell unsold food at a discount, customers order it, couriers deliver it,
operators keep an eye on the whole platform.

This is **frontend only**. All data is mock data held in memory; the `*Api.ts` files are stubs to be swapped
for `fetch()` calls to the real backend. **Backend developers: start with
[docs/BACKEND_INTEGRATION.md](docs/BACKEND_INTEGRATION.md).** Where the Phase 1 and Phase 2 documents disagree, this
frontend follows Phase 2.

## Run

```bash
cd APP/frontend
npm install
npm run dev        # http://localhost:5173
npm run build      # type-check + production build
```

Data and the session live in memory, so a page reload resets everything and logs you out.
Light/dark mode follows your system by default; the sun/moon button switches it and remembers your choice.

## Demo accounts

Password for every account: `demo123` (the login page has one-click buttons for the main ones).

| Role       | Email                | Notes                                         |
|------------|----------------------|-----------------------------------------------|
| Customer   | alice@demo.com       | Orders in every state                         |
| Customer   | bob@demo.com         | Has orders, one on its way                    |
| Restaurant | greenleaf@demo.com   | Green Leaf Bakery — **open**                  |
| Restaurant | sakura@demo.com      | Sakura Sushi — **paused**                     |
| Restaurant | tacoverde@demo.com   | Taco Verde — **closed**, all sold out         |
| Courier    | courier@demo.com     | Carl — starts offline                         |
| Courier    | dina@demo.com        | Dina — mid-delivery                           |
| Courier    | eli@demo.com         | Eli — offline                                 |
| Operator   | operator@demo.com    | Platform overview                             |

## What's in v1

- **Auth:** login, register (customer / restaurant / courier), fake session, each role locked to its own dashboard.
- **Customer:** discover restaurants (search, cuisine filters, "open now") on a list and a city map (click a pin to open the menu), menus with availability and savings,
  cart with delivery address and special instructions, confirm-then-place, order tracker
  (placed → accepted → preparing → ready → on the way → delivered), cancel while "placed".
  Ordering is disabled when the restaurant is paused/closed or everything is sold out.
  Once an order is ready or has a courier, a live map shows the route and the courier moving, with an ETA.
- **Restaurant:** open/paused/closed switch, live board (New → Preparation queue → Ready for pickup, oldest first),
  accept / reject with a reason / start preparing / mark ready, menu availability switches, order history.
- **Courier:** go online/offline, delivery requests (orders being prepared or ready with no courier), accept several
  at once (one courier → many orders), confirm pickup per order (once ready), mark delivered, earnings and history.
  A map shows every active route plus nearby pickup requests. Payouts are placeholders.
- **Operator:** city map (restaurants by status, couriers by state, active order addresses), platform stats, "needs attention" alerts, all orders with filters, cancel,
  restaurant status control, courier roster with live states.
- **Output console** on every dashboard: every action sent, the response (OK or error) and every error shown, timestamped. Collapsible. Every state-changing action asks for confirmation first.

## Where things live

```
src/
  domain.ts        TypeScript types for the domain model (User, Restaurant, Order, enums...) <- the contract
  mockData.ts      seed data, the in-memory "database" (MOCK ONLY, delete when the backend exists)
  session.tsx      fake session context + role -> dashboard path
  styles/          all CSS, split by area; tokens.css is the single colour palette (see below)
  ui/
    http.ts        fetch wrapper for the real backend (unused until the stubs are replaced)
    common.tsx     shared UI: Logo, theme toggle, badges, confirm button, order card + tracker, stats, useLoad
    icons.tsx      line-icon set (no emoji)
    OrderLifecycle.tsx   animated order journey on the login screen
    reg/           LoginPage, RegisterPage, AuthShell, regApi            (login / register / logout / couriers)
    ord/           RestaurantListPage, MenuPage (customer browse + restaurant manage), OrderCart, IncomingOrdersList, ordApi   (restaurants / menus / orders)
    dashboard/     DashboardRouter (route guards + layout), Customer / Restaurant / Courier / Operator pages,
                   OutputConsolePanel, dashboardApi (console log, send() wrapper, preparation queue and customer order status data)
    map/           MapView (MapLibre engine), CityMap (R-ORD-01), mapStyle (reads --map-* tokens), geo (distance, ETA, routes)
docs/BACKEND_INTEGRATION.md   endpoint table, order lifecycle, what is mock-only
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

Maps use **OpenStreetMap** data as vector tiles from [OpenFreeMap](https://openfreemap.org) (no API key), drawn with
[MapLibre GL](https://maplibre.org) in the app's own palette (land, parks, water, buildings and roads only; no labels or
POIs). Street routes come from the public **OSRM** demo server. Both need internet; without OSRM the route falls back to
a straight line. Once picked up, a delivery takes 3 minutes to cross the map in the demo (`SIM_DELIVERY_MS` in
`ui/map/geo.ts`). Seed coordinates are approximate, and new accounts get a random spot downtown (no geocoding yet).

No business rules live here; the backend will own validation and order state rules. The mock-only helpers are listed in
the integration guide.
