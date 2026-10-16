// Adapters: turn what the Spring Boot backend sends into the shapes in domain.ts, so no screen has to know
// about backend naming. When a *Api stub becomes a real call, map the response through these, for example:
//   export const listMenu = (id: string) => request<unknown>(`/restaurants/${id}/menu`).then(toMenuItems);
//
// What differs from the frontend types:
//   - enums are UPPERCASE on the backend (OPEN, PICKED_UP, SOLD_OUT), lowercase here
//   - coordinates are `latitude` / `longitude` (and `deliveryLatitude` / `deliveryLongitude`), `lat` / `lng` here
//   - several fields can be empty: restaurant coordinates and cuisine, original prices, names
//   - orders carry `lines`; some display fields (customerName, total, createdAt...) may not be sent yet
// The backend's controllers and DTOs don't exist yet, so the field names below follow its domain classes and the SQL
// schema (phase2). Adjust here, and only here, when the DTOs are final.
import type { CourierLocation, CourierState, MenuItem, MenuItemStatus, Order, OrderLine, OrderStatus, Restaurant, RestaurantStatus, Role } from '../domain';
import type { SessionUser } from './reg/regApi';

// Backend JSON isn't validated, so it is read loosely and every value is normalised here.
type Raw = any;

const lower = <T extends string>(value: unknown): T => String(value).toLowerCase() as T;
const text = (value: unknown, fallback = ''): string => (value === null || value === undefined ? fallback : String(value));
const optionalText = (value: unknown): string | undefined => (value === null || value === undefined || value === '' ? undefined : String(value));
/** DECIMAL columns may arrive as numbers or strings; empty stays undefined. */
const optionalNumber = (value: unknown): number | undefined => (value === null || value === undefined || value === '' ? undefined : Number(value));

/** Frontend enum value -> what the backend expects (`'picked_up'` -> `'PICKED_UP'`), for request bodies. */
export const toBackendEnum = (value: string): string => value.toUpperCase();

export function toRestaurant(j: Raw): Restaurant {
  return {
    id: text(j.id),
    name: text(j.name),
    cuisine: text(j.cuisine),
    address: text(j.address),
    status: lower<RestaurantStatus>(j.status),
    lat: optionalNumber(j.latitude ?? j.lat),
    lng: optionalNumber(j.longitude ?? j.lng),
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
    id: text(j.id),
    customerId: text(j.customerId),
    customerName: text(j.customerName, 'Customer'),
    deliveryAddress: text(j.deliveryAddress),
    deliveryLat: optionalNumber(j.deliveryLatitude ?? j.deliveryLat),
    deliveryLng: optionalNumber(j.deliveryLongitude ?? j.deliveryLng),
    restaurantId: text(j.restaurantId),
    restaurantName: text(j.restaurantName, 'Restaurant'),
    lines,
    specialInstructions: text(j.specialInstructions),
    status: lower<OrderStatus>(j.status),
    total: optionalNumber(j.total ?? j.totalCharges) ?? lines.reduce((sum, l) => sum + l.price * l.quantity, 0),
    createdAt: text(j.createdAt),
    rejectReason: optionalText(j.rejectReason),
    courierId: optionalText(j.courierId),
    courierName: optionalText(j.courierName),
    pickedUpAt: optionalText(j.pickedUpAt),
  };
}

/** Couriers without a known position can't be drawn, so they are left out. */
export function toCourierLocations(list: Raw[]): CourierLocation[] {
  return list.flatMap((j) => {
    const lat = optionalNumber(j.latitude ?? j.lat);
    const lng = optionalNumber(j.longitude ?? j.lng);
    if (lat === undefined || lng === undefined) return [];
    return [{ id: text(j.id ?? j.userId), name: text(j.name, 'Courier'), lat, lng, courierState: lower<CourierState>(j.courierState ?? j.status ?? 'offline') }];
  });
}

/** The signed-in user (login response, `GET /auth/me`, or a courier row). */
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
