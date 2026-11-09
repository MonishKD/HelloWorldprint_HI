// Adapters: turn what the Spring Boot backend sends into the shapes in domain.ts, so no screen has to know
// about backend naming. Every *Api call maps its response through these.
//
// What differs from the frontend types:
//   - enums are UPPERCASE on the backend (OPEN, PICKED_UP, SOLD_OUT), lowercase here
//   - coordinates are `latitude` / `longitude` (and `deliveryLatitude` / `deliveryLongitude`), `lat` / `lng` here
//   - several fields can be empty: restaurant coordinates and cuisine, original prices, names
//   - orders come as `orderId` with `instructions`; customer and restaurant names and the placed time aren't sent
//   - times come without a zone (`2026-11-07T22:04:35`) and are UTC: read through `time()`
//   - money from the pricing routes (checkout, summary, history) is in cents (`totalCents`), dollars here
// Adjust here, and only here, when a response changes.
import type { Checkout, CityView, CourierDashboard, CourierDelivery, CourierJob, CourierState, Fare, HistoryRow, LedgerEntryType, MenuItem, MenuItemStatus, Order, OrderLine, OrderStatus, OrderSummary, ReadyOrder, Restaurant, RestaurantStatus, Role, StalledOrder, WaitingOrder } from '../domain';
import type { SessionUser } from './reg/regApi';

// Backend JSON isn't validated, so it is read loosely and every value is normalised here.
type Raw = any;

const lower = <T extends string>(value: unknown): T => String(value).toLowerCase() as T;
const text = (value: unknown, fallback = ''): string => (value === null || value === undefined ? fallback : String(value));
const optionalText = (value: unknown): string | undefined => (value === null || value === undefined || value === '' ? undefined : String(value));
/** DECIMAL columns may arrive as numbers or strings; empty stays undefined. */
const optionalNumber = (value: unknown): number | undefined => (value === null || value === undefined || value === '' ? undefined : Number(value));
/**
 * The backend's times are LocalDateTime: no zone, and its clock is UTC (Docker), so they are read as UTC, like the
 * console does (toLogEntry). Without this the browser reads them as local time and they land hours in the future.
 */
const time = (value: unknown): string | undefined => {
  const t = optionalText(value);
  return t && !/(Z|[+-]\d\d:\d\d)$/.test(t) ? `${t}Z` : t;
};

/** Frontend enum value -> what the backend expects (`'picked_up'` -> `'PICKED_UP'`), for request bodies. */
export const toBackendEnum = (value: string): string => value.toUpperCase();

/** A restaurant registered through sign-up has no position yet and comes as 0, 0: it gets no map pin. */
export function toRestaurant(j: Raw): Restaurant {
  const lat = optionalNumber(j.latitude ?? j.lat);
  const lng = optionalNumber(j.longitude ?? j.lng);
  const placed = !(lat === 0 && lng === 0);
  return {
    id: text(j.id),
    name: text(j.name),
    cuisine: text(j.cuisine),
    address: text(j.address),
    status: lower<RestaurantStatus>(j.status),
    lat: placed ? lat : undefined,
    lng: placed ? lng : undefined,
  };
}

/** A discount only exists when an original price is stored; otherwise the item sells at its normal price. */
export function toMenuItem(j: Raw): MenuItem {
  const price = Number(j.price);
  return {
    id: text(j.id),
    restaurantId: text(j.restaurantId),
    name: text(j.name),
    description: text(j.description),
    price,
    originalPrice: optionalNumber(j.originalPrice) ?? price,
    status: lower<MenuItemStatus>(j.status),
  };
}

/** Accepts a plain array, or an object wrapping one (`RestaurantMenu` with `items` or `menu`). */
export function toMenuItems(j: Raw): MenuItem[] {
  const list: Raw[] = Array.isArray(j) ? j : (j?.items ?? j?.menu ?? []);
  return list.map(toMenuItem);
}

export function toOrderLine(j: Raw): OrderLine {
  const price = Number(j.price);
  return {
    itemId: text(j.menuItemId ?? j.itemId),
    name: text(j.name),
    price,
    originalPrice: optionalNumber(j.originalPrice) ?? price,
    quantity: Number(j.quantity),
  };
}

/**
 * The total falls back to the sum of the lines (it is derived data anyway); names, created time and pick-up time are
 * display fields the backend may not send yet. Without a created time the backend's own order (arrival order) is kept.
 */
export function toOrder(j: Raw): Order {
  const lines: OrderLine[] = (j.lines ?? []).map(toOrderLine);
  return {
    id: text(j.orderId ?? j.id),
    customerId: text(j.customerId),
    customerName: text(j.customerName, j.customerId ? `Customer #${j.customerId}` : 'Customer'),
    deliveryAddress: text(j.deliveryAddress),
    deliveryLat: optionalNumber(j.deliveryLatitude ?? j.deliveryLat),
    deliveryLng: optionalNumber(j.deliveryLongitude ?? j.deliveryLng),
    restaurantId: text(j.restaurantId),
    restaurantName: text(j.restaurantName, 'Restaurant'),
    lines,
    specialInstructions: text(j.instructions ?? j.specialInstructions),
    status: lower<OrderStatus>(j.status),
    total: optionalNumber(j.total ?? j.totalCharges) ?? lines.reduce((sum, l) => sum + l.price * l.quantity, 0),
    createdAt: time(j.createdAt) ?? '',
    rejectReason: optionalText(j.rejectReason),
    courierId: optionalText(j.courierId),
    courierName: optionalText(j.courierName),
    pickedUpAt: time(j.pickedUpAt),
    deliveredAt: time(j.deliveredAt),
  };
}

/** The signed-in user (`GET /auth/session`). The username doubles as the display name. */
export function toSessionUser(j: Raw): SessionUser {
  const username = text(j.username);
  return {
    id: text(j.id ?? j.userId ?? j.userID),
    username,
    name: text(j.name, username),
    email: text(j.email),
    role: lower<Role>(j.role),
    restaurantId: optionalText(j.restaurantId),
    address: optionalText(j.address),
    lat: optionalNumber(j.latitude ?? j.lat),
    lng: optionalNumber(j.longitude ?? j.lng),
    courierState: optionalText(j.courierState ?? j.status) ? lower<CourierState>(j.courierState ?? j.status) : undefined,
  };
}

function toCourierJob(j: Raw): CourierJob {
  return {
    orderId: text(j.orderId),
    status: lower<OrderStatus>(j.status),
    restaurantName: text(j.restaurantName),
    pickupAddress: text(j.pickupAddress),
    pickupLat: optionalNumber(j.pickupLatitude),
    pickupLng: optionalNumber(j.pickupLongitude),
    destinationName: text(j.destinationName),
    destinationAddress: text(j.deliveryAddress),
    destinationLat: optionalNumber(j.deliveryLatitude),
    destinationLng: optionalNumber(j.deliveryLongitude),
    donation: Boolean(j.shelterId),
    lines: (j.lines ?? []).map((l: Raw) => ({ name: text(l.name), quantity: Number(l.quantity) })),
    specialInstructions: text(j.instructions),
    pickedUpAt: time(j.pickedUpAt),
  };
}

export function toCourierDashboard(j: Raw): CourierDashboard {
  return {
    state: lower<CourierState>(j.status),
    offer: j.offer ? toCourierJob(j.offer) : null,
    order: j.currentOrder ? toCourierJob(j.currentOrder) : null,
  };
}

export function toReadyOrder(j: Raw): ReadyOrder {
  return {
    orderId: text(j.orderId),
    restaurantName: text(j.restaurantName),
    pickupAddress: text(j.pickupAddress),
    itemCount: Number(j.itemCount),
    readyAt: time(j.readyAt) ?? '',
  };
}

export function toCourierDelivery(j: Raw): CourierDelivery {
  return {
    orderId: text(j.orderId),
    restaurantName: text(j.restaurantName),
    destinationName: text(j.destinationName),
    deliveredAt: time(j.deliveredAt) ?? '',
  };
}

export function toCityView(j: Raw): CityView {
  return {
    restaurants: (j.restaurants ?? []).map((r: Raw) => ({
      ...toRestaurant(r),
      queued: Number(r.queued),
      inPreparation: Number(r.inPreparation),
      ready: Number(r.ready),
      soldOutItems: (r.soldOutItems ?? []).map(String),
    })),
    couriers: (j.couriers ?? []).map((c: Raw) => ({
      id: text(c.id),
      name: text(c.name),
      lat: Number(c.latitude),
      lng: Number(c.longitude),
      courierState: lower<CourierState>(c.status),
    })),
  };
}

export function toWaitingOrder(j: Raw): WaitingOrder {
  return { orderId: text(j.orderId), restaurantName: text(j.restaurantName), waitingSince: time(j.waitingSince) ?? '' };
}

export function toStalledOrder(j: Raw): StalledOrder {
  return { orderId: text(j.orderId), restaurantName: text(j.restaurantName), courierName: text(j.courierName) };
}

// ---------- Pricing (PRC): money comes in cents ----------

const dollars = (cents: unknown): number => Number(cents ?? 0) / 100;

/** The fare parts every pricing response carries. History rows call the total `amountCents`. */
function toFare(j: Raw): Fare {
  return {
    subtotal: dollars(j.subtotalCents),
    deliveryFee: dollars(j.deliveryFeeCents),
    serviceFee: dollars(j.serviceFeeCents),
    surcharge: dollars(j.surchargeCents),
    total: dollars(j.totalCents ?? j.amountCents),
  };
}

export function toCheckout(j: Raw): Checkout {
  return { paymentId: text(j.paymentId), clientSecret: text(j.clientSecret), fare: toFare(j) };
}

export function toOrderSummary(j: Raw): OrderSummary {
  return {
    orderId: text(j.orderId),
    restaurantName: text(j.restaurantName, 'Restaurant'),
    courierName: optionalText(j.courierName),
    deliveryAddress: text(j.deliveryAddress),
    lines: (j.lines ?? []).map((l: Raw) => ({ name: text(l.name), quantity: Number(l.quantity), price: dollars(l.priceCents) })),
    fare: toFare(j),
    placedAt: time(j.placedAt) ?? '',
    deliveredAt: time(j.deliveredAt) ?? '',
    cardBrand: optionalText(j.cardBrand),
    cardLast4: optionalText(j.cardLast4),
  };
}

/** A courier's row has no money keys at all (`entryType` included), so that is what tells the two apart. */
export function toHistoryRow(j: Raw): HistoryRow {
  if (j.entryType === undefined || j.entryType === null) return toCourierDelivery(j);
  return {
    orderId: text(j.orderId),
    restaurantName: text(j.restaurantName, 'Restaurant'),
    entryType: lower<LedgerEntryType>(j.entryType),
    finalStatus: lower<OrderStatus>(j.finalStatus),
    fare: toFare(j),
    placedAt: time(j.placedAt) ?? '',
    finishedAt: time(j.finishedAt) ?? '',
  };
}
