# SOEN 343 Project

FortunateGoods: food surplus delivery app, in two parts:

- `app/backend`: the server (Java, Spring Boot). Handles accounts, restaurants and orders.
- `app/frontend`: the website (React). What users see in the browser.

## Requirements

- Java 25
- Node.js 20.19 or newer
- Docker Desktop (to run everything with one command, see Docker startup)
- An internet connection: card payments go through Stripe (test mode) and the map tiles come from OpenFreeMap

No need to install Maven: the backend uses the Maven wrapper (`mvnw`), which downloads the right Maven version the first time you run it.

## Backend

Compiles the Java code, runs the tests and packages the server into a `.jar` file in `app/backend/target/`.

```bash
cd app/backend
./mvnw package        # Mac
.\mvnw.cmd package    # Windows
```

The first run takes a few minutes while Maven downloads the libraries.

## Frontend

Installs the frontend's packages (React, Vite, etc.) into `node_modules/`, using the exact versions in `package-lock.json` so everyone has the same setup.

```bash
cd app/frontend
npm install
```

## Docker startup

One command starts everything: the database, the backend and the frontend, each in its own container ("mini computer"). You do not need Java, Node or Postgres installed for this, only Docker Desktop (make sure it is open and running).

**Step 1: make your `.env` file.** Docker reads it from the ROOT of the repo (next to `docker-compose.yml`, not inside `app/`). Copy the example and fill it in. Passwords are on discord.

```bash
cp .env.example .env
```

What each line means:

- `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`: the database that gets created the first time. Pick whatever, but the backend uses the same ones so you only write them once.
- `DB_PORT`: the door on YOUR computer that leads to the database. Use 9090 if you want the same as me. (Inside docker the backend talks to the database on 5432 by itself, compose handles that.)
- `BACKEND_PORT`: use 8080 if you want the same as me.
- `FRONTEND_PORT`: use 5173 if you want the same as me. The backend uses this one to know which website it accepts requests from (CORS), so it must match the port you open the site on.
- `SEED_MODE`: `keep` only fills the database with starting data if it is empty. `reset` wipes it and seeds again. If you leave it out it defaults to `keep`.
- `VITE_API_URL`: where your BROWSER calls the backend. Leave it as in the example.
- `STRIPE_SECRET_KEY` (`sk_test_...`) and `VITE_STRIPE_PUBLISHABLE_KEY` (`pk_test_...`): the Stripe **test mode** keys, for card payments. Get them from the [Stripe dashboard](https://dashboard.stripe.com/test/apikeys) (switch on Test mode, then Developers → API keys), or ask on discord for the team's. Never put live keys (`sk_live_...`) here. Without real test keys the app still starts, but checkout fails.

If you forget one, compose stops and tells you which one is missing (I know you will forget lol).

**Step 2: build and start.** From the root of the repo (the folder with `docker-compose.yml`), type this in your terminal:

```bash
docker compose up --build
```

Reading it word by word:

- `docker compose`: the tool that reads `docker-compose.yml` and manages all 3 containers together.
- `up`: means "start everything". It creates the containers if they don't exist yet, then runs them.
- `--build`: means "build the images again before starting". Without it, docker would reuse the old build it remembers and NOT see your new code. So use it every time you changed something. (A flag is just an option you stick on the end of a command to change how it behaves. `--` + a word is the long name of the option.)

The first time takes a few minutes since it downloads the images and builds the backend. The order is handled for you: database first, then backend, then frontend. The terminal will fill with logs from all 3, that's normal. It keeps running as long as the app is running. Press `Ctrl + C` in that terminal to stop it.

If you want your terminal back while it runs, add `-d` at the end (`docker compose up --build -d`). `-d` means "detached": it runs in the background and prints nothing. To see the logs again, type `docker compose logs`. (`-d` is the short name of the option, short names use a single `-` and one letter.)

**Step 3: open it.**

- Website: `http://localhost:5173` (or your `FRONTEND_PORT`)
- Backend: `http://localhost:8080` (or your `BACKEND_PORT`)

**Stopping it:** if you used `Ctrl + C` it already stopped, but the containers still exist (just turned off). To clean them up, or to stop it when you used `-d`, type:

```bash
docker compose down
```

- `down`: the opposite of `up`. It stops all 3 containers and removes them. Removing them is fine, they get created again with `up`. Your database data is NOT lost, it lives in a volume (a storage place outside the container, called `database_data`) that survives this.

If you want a completely fresh start, with the database emptied too:

```bash
docker compose down -v
```

- `-v`: short for "volumes". It also deletes the volume, so ALL the data in the database is gone.
- Nextt time you use `up` , it creates an empty database and seeds it again.

If you only want to re-seed without deleting everything by hand, set `SEED_MODE=reset` in your `.env`, start once, then put it back to `keep`. Otherwise it wipes your data on every start, SO PLEASE DON'T KEEP IT AT RESET.

The explanation of every line is in the comments of `docker-compose.yml`, `app/backend/Dockerfile` and `app/frontend/Dockerfile`. I wrote them step by step, so read them if you want to know why it works and how.

## Docker FAQ

**Do I need to rebuild after every change I make?**

Yes, if you run the app through docker. The containers are built with a COPY of your code from the moment you ran `--build`. After that, they don't look at your files anymore. So if you edit a file and just refresh, nothing changes. You need `docker compose up --build` again to get your new code in. That is also why building every single time is slow, and why we don't want to develop like that.

**So how do I work day to day?**

Run things on your own computer while you code, and use docker only for the database. That is fast, and the backend and frontend reload quicker than rebuilding images.

1. Start only the database container (from the root of the repo):

   ```bash
   docker compose up database -d
   ```

   `database` at the end means "only start this service, not the other two". `-d` runs it in the background.
2. Start the backend on your computer, in a second terminal:

   ```bash
   cd app/backend
   ./mvnw spring-boot:run        # Mac
   .\mvnw.cmd spring-boot:run    # Windows
   ```

   It reads the same root `.env` file as docker, so there is nothing extra to fill in. `DB_HOST` is not in `.env`, so it defaults to `localhost`, which is where your database container is (through `DB_PORT`).
3. Start the frontend on your computer, in a third terminal:

   ```bash
   cd app/frontend
   npm run dev
   ```

   Use `Ctrl + C` in each terminal to stop them. Stop the database with `docker compose down`.

When you change backend code, stop it (`Ctrl + C`) and run it again. The frontend updates by itself when you save a file.


**Wait but, how do they talk to each other if only the database is in docker?**

The database container opens a door on your computer. That is the `DB_PORT:5432` line in `docker-compose.yml`, so from your computer the database is just at `localhost:9090` (or whatever your `DB_PORT` is), even though it lives in a container. The backend you run locally uses `DB_HOST=localhost` (its default) and the `DB_PORT` from the root `.env`, so it knocks on that door and gets to the database. Then the backend listens on 8080 and your browser sends the frontend's requests there, using `VITE_API_URL`. All 3 read the same root `.env`, so they agree on the ports. 

The only difference from full docker running is that the backend calls `localhost:${DB_PORT}`(bascially docker listens to that port on your machine and lets it through)  instead of `database:5432`(which only docker uses between each of its containers because the containers share a network and your computer doesn't (nothing you need to worry about)).

 Feel free to read the comments in the Dockerfiles and docker-compose.yml for more info or ask AI to summerize.


**Should it run locally until the very end then?**

No. Use the full docker (`docker compose up --build`) at these moments:

- Before you open a PR, to check that it still builds and starts as a whole.
- When you pull other people's changes and want to see everything working together.
- For the demo, because it is the same on every computer. Nobody needs Java or Node installed.

A nice habit: develop locally, then do one docker build before you push.

**Will rebuilding break or change my backend or my data?**

No. A rebuild only replaces the backend and frontend containers with a new copy of your code. Your database is a separate container with its data in a volume, and that is left alone. Your data is only touched if you type `docker compose down -v` or you set `SEED_MODE=reset`. With `SEED_MODE=keep`, the seed data is only added when the database is empty, so rebuilding does not duplicate it.

**Docker is stuck on old code or acting weird, what do I do?**

Try `docker compose up --build` first. If it still acts weird, do `docker compose down` then `docker compose up --build`. Last resort is `docker compose down -v` to also delete the database data (everything gets seeded again).

## Running the app

Use the Docker startup above: `docker compose up --build`, then open the website at `http://localhost:5173`.
Every seeded account uses the password `demo123`:

| Role | Accounts |
|---|---|
| Customer | `alice`, `bob` |
| Restaurant | `pouletrouge` and `uniburger` (open, with items), `fromagerie` (open, everything sold out), `atwater` (open, empty menu), `boustan`, `pholambert`, `pizzeriaguy`, `bagelclark` (paused), `lolarosa`, `myriade`, `sushibishop`, `taqueria` (closed) |
| Courier | `carl`, `dana`, `eli` (on duty), `fay` (off duty) |
| Operator | `operator` |

**Back to the initial state:** sign in as `operator` and press **Reset** in the information area at the top. It wipes every
order and change and seeds again, without restarting anything. The other way is `SEED_MODE=reset` in `.env` for one start
(then back to `keep`).

**Card payments (Stripe test mode):** in the cart, Continue to payment shows the fees, then Stripe's card form. The money is
only held on the card when the order is placed, and taken when the courier delivers it (cancelled or rejected: released).

| Card number | Result |
|---|---|
| `4242 4242 4242 4242` | Paid |
| `4000 0000 0000 0002` | Declined: the message shows in the cart and the console, nothing is placed |

Any future expiry date and any 3-digit CVC. Leave Stripe's optional "save my information" (Link) fields empty.

**Pricing values:** the `pricing` block of `app/backend/src/main/resources/seed-config.json`. Nothing about fees is written in the code.

| Value | Seeded |
|---|---|
| `deliveryFee` | $2.99 per order |
| `serviceFeePercent` | 10% of the subtotal, rounded to the cent |
| `smallOrderThreshold` / `smallOrderSurcharge` | $2.00 added when the subtotal is under $15.00 |

They are read when the backend starts, so after changing one run `docker compose up --build` (Reset doesn't reload them).
Example: one Poulet Rouge bowl is $7.50 + $2.99 + $0.75 + $2.00 = **$13.24**.

## Phase III demo

Start with Reset (operator). Use one normal and one private browser window, since both tabs of one window share a sign-in.
The output console on the right lists every command and the backend's answer.

1. **Happy path**: `alice` orders a Poulet Rouge bowl and pays with `4242…`. `pouletrouge` accepts, starts preparing and
   marks it ready. The nearest idle courier (`carl`) gets the offer on his dashboard without a reload; he accepts, confirms
   pickup and delivery. In alice's My orders, Order summary shows each fee and the total charged ($13.24), matching the
   hand calculation above and the payment captured in the Stripe test dashboard.
2. **No courier available**: `carl`, `dana` and `eli` go off duty (`fay` already is). An order marked ready waits in the
   dispatch queue: the operator sees the alert and the order under Delivery. As soon as a courier goes on duty, it is offered to them.
3. **Sold out / paused**: Lentil soup at Poulet Rouge is sold out and can't be added; Boustan is paused, so ordering is off.
   The operator pauses Uniburger mid-session: its pin turns red and dashed on the map, and an order there is refused with
   "Restaurant is paused, try again later" (the held amount is released).
4. **Dinner rush**: 6 orders accepted at Poulet Rouge turn its pin red ("Poulet Rouge · 6 queued", amber from 3), and the
   operator gets "Poulet Rouge has 6 orders queued: consider pausing it". The operator pauses it from the Orders tab.
   Downtown pins sit close together: zoom in once or twice on the map to show it.

## Phase II demo

Start from a fresh database (`SEED_MODE=reset` once, then back to `keep`). Use two browser windows, a normal one and a private
one, because both tabs of the same window share one sign-in.

1. **Customer** (`alice`): the restaurant list shows open, paused and closed restaurants. Open Poulet Rouge, add a bowl to the
   cart, enter an address and place the order. My orders shows it as `placed`.
2. **Restaurant** (`pouletrouge`, private window): the order is under New. Accept it (it enters the preparation queue as next),
   start preparing, mark it ready. Each step shows up in the customer's My orders after a refresh.
3. **Output console**: every action and the backend's answer (with the order event id) is listed on the right.

Things that must be refused, each with the backend's reason shown on screen:

| Try | Answer |
|---|---|
| Open `http://localhost:8080/api/restaurants` without signing in | 401, sign in first |
| Register with the operator role (not offered in the form; the API refuses it too) | 400, operators can't be registered |
| Order from Boustan (paused) | ordering is disabled; the API answers 409 "Restaurant is paused" |
| Order Poulet Rouge's sold-out Lentil soup | the item can't be added; the API answers 409 "sold out" |
| Customer cancels after the restaurant accepted | Cancel is gone; the API answers 409 "Only PLACED orders can be cancelled" |
| A customer accepting an order (wrong role) | 403 |
| Marking ready an order that is still placed (skipped state) | Mark ready isn't offered; the API answers 409 |
