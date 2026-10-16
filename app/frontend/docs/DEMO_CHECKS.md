# Repeatable demo checks (seeded accounts)

Story #42 wants the demo to start from the same accounts every time. Run these by hand before a demo; it takes about five
minutes. Reloading the page resets the mock data, so every run starts the same.

Every seeded account uses the password `demo123`. Log in with the email or the username.

## 1. Every role can sign in (#73)

| Account | Role | Expected result |
|---|---|---|
| `alice@demo.com` (or `alice`) | customer | Opens the customer dashboard (`/customer`), role "customer" in the top bar |
| `greenleaf@demo.com` (or `greenleaf`) | restaurant | Opens the Green Leaf Bakery dashboard (`/restaurant`) and nothing of other restaurants |
| `courier@demo.com` (or `carl`) | courier | Opens the courier dashboard (`/courier`), offline |
| `operator@demo.com` (or `operator`) | operator | Opens the operator dashboard (`/operator`) |

A wrong password shows "Invalid username/email or password." and leaves you on the login page.

## 2. The operator can't be registered (#73)

1. Open `/register`. Only Customer, Restaurant and Courier are offered.
2. If the API is called directly with the operator role, it refuses with "That role cannot be registered."

## 3. Sessions (#64)

1. Log in as any role, then type another role's address, for example `/restaurant` as the customer. You are sent back to your own dashboard.
2. Click Log out. The login page says "You have been logged out." and the output console is cleared.
3. Type a dashboard address while logged out. You land on the login page.

## 4. Registration (#59, #143)

1. Register as a restaurant (name and address are required). The account opens an empty dashboard of its own, the restaurant is closed, and the page says to add a menu item first.
2. Try to open it with an empty menu. It is refused with a message, both in place and in the console. The backend has to enforce this too.
3. Add an item in "Today's menu", then open the restaurant. The customer list now shows it as open.
4. Register with a username or email that is already taken. You get the message and nothing is created.

## 5. Catalog (#78)

1. Log in as `alice`. Three restaurants are listed: Green Leaf (open), Sakura Sushi (paused) and Taco Verde (closed), each linking to its own menu.
2. Open Sakura Sushi. A banner says it is paused and every "Add" button is disabled.

## What the frontend can't check

Some parts of issues #73, #64, #59 and #78 can only be checked against the real backend:
- the seed loader (#25, #26) actually created the accounts
- Spring Security refuses a protected request without a session (401)
- the controller refuses duplicate usernames and emails, and the operator role
- the restaurant and its owner link are saved together

Repeat the checks above once `regApi` and `ordApi` are connected.
