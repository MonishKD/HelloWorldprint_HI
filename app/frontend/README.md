# FortunateGoods frontend (v1)

Surplus-food delivery: restaurants sell unsold food at a discount, customers order it, couriers deliver it,
operators keep an eye on the whole platform.

This is the React frontend. It talks to the Spring Boot backend in `app/backend` through `src/ui/http.ts`
(session cookie + CSRF header), and every call lives in one of the `*Api.ts` files. `src/ui/adapters.ts` turns the
backend's JSON into the types the screens use. Where the Phase 1 and Phase 2 documents disagree, the frontend follows Phase 2.

## Run

The backend and its database have to be running first. From the repo root, with `.env` filled in (see `.env.example`):

```bash
docker compose up -d database backend   # PostgreSQL + the API on http://localhost:8080
cd app/frontend
npm install
npm run dev        # http://localhost:5173
npm run build      # type-check + production build
```

The session is the backend's cookie, so a page reload keeps you signed in.
It needs Node.js 20.19 or newer. Light or dark mode follows your system by default, and the sun/moon button switches it and remembers your choice.

## Seeded accounts

The backend seeds these from `app/backend/src/main/resources/seed-config.json`. Every account uses the password `demo123`,
and you can log in with the username or the email (`<username>@example.com`).

| Role       | Username      | Notes                                              |
|------------|---------------|----------------------------------------------------|
| Customer   | alice, bob    |                                                    |
| Restaurant | pouletrouge   | Poulet Rouge, open                                 |
| Restaurant | uniburger     | Uniburger, open                                    |
| Restaurant | boustan       | Boustan, paused                                    |
| Restaurant | lolarosa      | Lola Rosa, closed (9 more restaurants in the seed) |
| Courier    | carl          | Signs in; the courier dashboard isn't in Phase II  |
| Operator   | operator      | Signs in; the operator dashboard isn't in Phase II |

## What's in Phase II

Auth: log in with a username or email, or register as a customer, restaurant or courier (never operator). A new restaurant
starts closed. Each role is locked to its own dashboard, and the login page says when you were logged out or your session expired.

Customer: browse restaurants as a list or on a city map (search, "open now"; click a pin to open its menu).
Menus show availability and savings. The cart takes a delivery address and special instructions, and asks for confirmation before
placing the order. The tracker follows an order through placed, accepted, preparing and ready, and a
customer can cancel while it is still "placed". Ordering is disabled when the restaurant is paused or closed, or when
everything is sold out.

Restaurant: an open/paused/closed switch and a live board (New, Preparation queue, Ready for pickup, oldest first). Staff can
accept, reject with a reason, start preparing and mark ready. There is a menu editor (add, edit, remove, availability),
an empty state that walks a new restaurant through adding items and then opening, and an order history.

Courier and operator: they can sign in, but their dashboards aren't part of Phase II yet.
