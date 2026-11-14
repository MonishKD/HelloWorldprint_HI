# FortunateGoods

SOEN 343, section HI, team HelloWorldprint.

A surplus-food delivery platform for Montreal: restaurants sell unsold food at a discount, customers order and pay by card,
couriers deliver, and an operator watches the platform. Each role has its own dashboard.

## Run it

Needs only **Docker Desktop** (running) and **internet**. No `.env` to set up, everything is in `docker-compose.yml`.

Tested on macOS 27.0 (Apple Silicon) with Docker Desktop 4.95.0 (Engine 29.8.2, Compose 5.6.0).

| | macOS | Windows |
|---|---|---|
| Install | [Docker Desktop for Mac](https://docs.docker.com/desktop/setup/install/mac-install/) (Apple chip or Intel chip version) | [Docker Desktop for Windows](https://docs.docker.com/desktop/setup/install/windows-install/) (keep "Use WSL 2" checked, restart if asked) |
| Terminal | Terminal | PowerShell |
| Commands | the ones below | the same ones below |

```bash
git clone https://github.com/MonishKD/HelloWorldprint_HI.git
cd HelloWorldprint_HI
git checkout phase3
docker compose up --build
```

When the logs show `Started FortunateGoodsApplication`, open **http://localhost:5173**.

Stop: `Ctrl + C`, then `docker compose down`. Fresh database: `docker compose down -v`.

## Accounts

Every account uses the password **`demo123`**. Use a private window to be signed in as two roles at once.

**Customers**

| Username | Password |
|---|---|
| `alice` | `demo123` |
| `bob` | `demo123` |

**Couriers**

| Username | Password | Starts |
|---|---|---|
| `carl` | `demo123` | on duty |
| `dana` | `demo123` | on duty |
| `eli` | `demo123` | on duty |
| `fay` | `demo123` | off duty |

**Operator**

| Username | Password | Note |
|---|---|---|
| `operator` | `demo123` | its **Reset** button restores the starting data |

**Restaurants**

| Username | Password | Restaurant | Starts |
|---|---|---|---|
| `pouletrouge` | `demo123` | Poulet Rouge | open |
| `uniburger` | `demo123` | Uniburger | open |
| `fromagerie` | `demo123` | Fromagerie Saint-Laurent | open, all sold out |
| `atwater` | `demo123` | Bistro Atwater | open, empty menu |
| `boustan` | `demo123` | Boustan | paused |
| `pholambert` | `demo123` | Pho Lambert | paused |
| `pizzeriaguy` | `demo123` | Pizzeria Guy | paused |
| `bagelclark` | `demo123` | Bagel Clark | paused |
| `lolarosa` | `demo123` | Lola Rosa | closed |
| `myriade` | `demo123` | Café Myriade | closed |
| `sushibishop` | `demo123` | Sushi Bishop | closed |
| `taqueria` | `demo123` | Taqueria Peel | closed |

## Card payments (Stripe test mode)

| Card number | Expiry (MM/YY) | CVC | Result |
|---|---|---|---|
| `4242 4242 4242 4242` | `12/30` | `123` | Paid |
| `4000 0000 0000 0002` | `12/30` | `123` | Declined |

Country: Canada. Leave the optional "save my information" fields empty.

## Problems

- **Port already allocated**: add a `.env` next to `docker-compose.yml` with e.g. `FRONTEND_PORT=5174` and run again.
- **Anything else**: `docker compose down -v`, then `docker compose up --build`.

Developer setup: [README-dev.md](README-dev.md).
