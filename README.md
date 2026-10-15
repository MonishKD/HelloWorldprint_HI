# SOEN 343 Project

FortunateGoods: food surplus delivery app, in two parts:

- `app/backend`: the server (Java, Spring Boot). Handles accounts, restaurants and orders.
- `app/frontend`: the website (React). What users see in the browser.

## Requirements

- Java 25
- Node.js 20.19 or newer

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

## Running the app

Not set up yet: the backend needs its database first, and the frontend needs its pages.
