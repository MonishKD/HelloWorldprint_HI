# Frontend image: build the React app once, then serve the built files with nginx (demo platform and docker compose).
# For hot reload while coding, run `npm run dev` on your machine instead.

# BUILD stage: Node compiles the TypeScript and bundles the app into dist/.
FROM node:22-alpine AS build
WORKDIR /frontend
COPY package.json package-lock.json ./
RUN npm ci
COPY . .

# VITE_* values are written into the bundle at build time, not read when the container starts.
# /api is relative: the browser calls the same address that served the page, and nginx passes it to the backend.
ARG VITE_API_URL=/api
# Stripe's publishable key (pk_test_...). Empty by default: card payments then show a warning instead of the card field.
ARG VITE_STRIPE_PUBLISHABLE_KEY=
ENV VITE_API_URL=$VITE_API_URL VITE_STRIPE_PUBLISHABLE_KEY=$VITE_STRIPE_PUBLISHABLE_KEY
RUN npm run build

# RUN stage: nginx serves dist/ on port 80 and proxies /api/ to the backend (see nginx.conf).
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /frontend/dist /usr/share/nginx/html
EXPOSE 80
