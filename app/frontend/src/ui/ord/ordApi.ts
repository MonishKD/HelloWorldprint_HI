import { db } from '../../mockData';
import type { CourierLocation, MenuItem, MenuItemStatus, Order, OrderLine, OrderStatus, Restaurant, RestaurantStatus } from '../../domain';

export type RestaurantSummary = Restaurant & { itemCount: number; availableCount: number; bestDiscount: number };

// Callers get copies so React state never aliases the in-memory db.
const copy = <T>(value: T): T => structuredClone(value);

const ACTIVE: OrderStatus[] = ['placed', 'accepted', 'preparing', 'ready', 'picked_up'];

/** Mock-only: mirrors what the backend will do to a courier's state when their orders change. */
function syncCourierState(courierId: string) {
  const courier = db.users.find((u) => u.id === courierId);
  if (!courier) return;
  const active = db.orders.filter((o) => o.courierId === courierId && ACTIVE.includes(o.status));
  courier.courierState = active.some((o) => o.status === 'picked_up') ? 'carrying' : active.length ? 'assigned' : 'idle';
}

// TODO: replace with fetch() to the real backend: GET /restaurants
export async function listRestaurants(): Promise<RestaurantSummary[]> {
  return db.restaurants.map((r) => {
    const items = db.menuItems.filter((m) => m.restaurantId === r.id);
    const bestDiscount = Math.max(0, ...items.map((m) => Math.round((1 - m.price / m.originalPrice) * 100)));
    return { ...copy(r), itemCount: items.length, availableCount: items.filter((m) => m.status === 'available').length, bestDiscount };
  });
}

// City map data (R-ORD-01): where every courier is and what they're doing.
// TODO: replace with fetch() to the real backend: GET /couriers/locations
export async function listCourierLocations(): Promise<CourierLocation[]> {
  return db.users
    .filter((u) => u.role === 'courier' && u.lat !== undefined && u.lng !== undefined)
    .map((u) => ({ id: u.id, name: u.name, lat: u.lat!, lng: u.lng!, courierState: u.courierState ?? 'offline' }));
}

// TODO: replace with fetch() to the real backend: GET /restaurants/{id}
export async function getRestaurant(id: string): Promise<Restaurant> {
  const restaurant = db.restaurants.find((r) => r.id === id);
  if (!restaurant) throw new Error('Restaurant not found.');
  return copy(restaurant);
}

// TODO: replace with fetch() to the real backend: PATCH /restaurants/{id}/status  { status }
// The backend refuses (409) opening a restaurant that has no menu items; the mock mirrors that so the UI's refusal path is real.
export async function setRestaurantStatus(id: string, status: RestaurantStatus): Promise<Restaurant> {
  const restaurant = db.restaurants.find((r) => r.id === id);
  if (!restaurant) throw new Error('Restaurant not found.');
  if (status === 'open' && !db.menuItems.some((m) => m.restaurantId === id)) {
    throw new Error('Add at least one menu item before opening your restaurant.');
  }
  restaurant.status = status;
  return copy(restaurant);
}

// TODO: replace with fetch() to the real backend: GET /restaurants/{id}/menu
export async function listMenu(restaurantId: string): Promise<MenuItem[]> {
  return copy(db.menuItems.filter((m) => m.restaurantId === restaurantId));
}

/** `originalPrice` is only set for discounted items. */
export type MenuItemInput = Pick<MenuItem, 'name' | 'description' | 'price' | 'status'> & { originalPrice?: number };

/** Mock-only: the backend refuses invalid items (400) with a reason; the owning restaurant comes from the session. */
function checkMenuItem(input: MenuItemInput) {
  if (!input.name.trim()) throw new Error('Give the item a name.');
  if (!(input.price > 0)) throw new Error('The price must be above zero.');
}

// TODO: replace with fetch() to the real backend: POST /restaurants/{restaurantId}/menu
export async function addMenuItem(restaurantId: string, input: MenuItemInput): Promise<MenuItem> {
  checkMenuItem(input);
  const item: MenuItem = { id: `m-${Date.now().toString(36)}`, restaurantId, ...input, originalPrice: input.originalPrice ?? input.price, name: input.name.trim(), description: input.description.trim() };
  db.menuItems.push(item);
  return copy(item);
}

// TODO: replace with fetch() to the real backend: PATCH /restaurants/{restaurantId}/menu/{itemId}
export async function updateMenuItem(restaurantId: string, itemId: string, input: MenuItemInput): Promise<MenuItem> {
  const item = db.menuItems.find((m) => m.id === itemId && m.restaurantId === restaurantId);
  if (!item) throw new Error('Menu item not found.');
  checkMenuItem(input);
  Object.assign(item, input, { originalPrice: input.originalPrice ?? input.price, name: input.name.trim(), description: input.description.trim() });
  return copy(item);
}

// TODO: replace with fetch() to the real backend: DELETE /restaurants/{restaurantId}/menu/{itemId}
// The backend hides the item but keeps past orders intact (order lines carry their own name and price).
export async function removeMenuItem(restaurantId: string, itemId: string): Promise<void> {
  const index = db.menuItems.findIndex((m) => m.id === itemId && m.restaurantId === restaurantId);
  if (index === -1) throw new Error('Menu item not found.');
  db.menuItems.splice(index, 1);
}

// TODO: replace with fetch() to the real backend: PATCH /menu-items/{id}/status
export async function setMenuItemStatus(itemId: string, status: MenuItemStatus): Promise<MenuItem> {
  const item = db.menuItems.find((m) => m.id === itemId);
  if (!item) throw new Error('Menu item not found.');
  item.status = status;
  return copy(item);
}

// TODO: replace with fetch() to the real backend: POST /orders
export async function placeOrder(input: {
  customerId: string;
  customerName: string;
  deliveryAddress: string;
  deliveryLat: number;
  deliveryLng: number;
  restaurant: Restaurant;
  lines: OrderLine[];
  specialInstructions: string;
}): Promise<Order> {
  const order: Order = {
    id: String(1001 + db.orders.length),
    customerId: input.customerId,
    customerName: input.customerName,
    deliveryAddress: input.deliveryAddress.trim(),
    deliveryLat: input.deliveryLat,
    deliveryLng: input.deliveryLng,
    restaurantId: input.restaurant.id,
    restaurantName: input.restaurant.name,
    lines: input.lines,
    specialInstructions: input.specialInstructions.trim(),
    status: 'placed',
    total: input.lines.reduce((sum, l) => sum + l.price * l.quantity, 0),
    createdAt: new Date().toISOString(),
  };
  db.orders.push(order);
  return copy(order);
}

// TODO: replace with fetch() to the real backend: GET /orders  (operator)
export async function listAllOrders(): Promise<Order[]> {
  return copy(db.orders);
}

// TODO: replace with fetch() to the real backend: GET /restaurants/{id}/orders
export async function listRestaurantOrders(restaurantId: string): Promise<Order[]> {
  return copy(db.orders.filter((o) => o.restaurantId === restaurantId));
}

// TODO: replace with fetch() to the real backend: GET /couriers/{id}/orders
export async function listCourierOrders(courierId: string): Promise<Order[]> {
  return copy(db.orders.filter((o) => o.courierId === courierId));
}

/** Orders being prepared or ready that no courier has claimed yet (the dispatch queue). */
// TODO: replace with fetch() to the real backend: GET /dispatch-queue
export async function listDispatchQueue(): Promise<Order[]> {
  return copy(db.orders.filter((o) => (o.status === 'preparing' || o.status === 'ready') && !o.courierId));
}

// TODO: replace with fetch() to the real backend: POST /orders/{id}/assignment
export async function assignCourier(orderId: string, courier: { id: string; name: string }): Promise<Order> {
  const order = db.orders.find((o) => o.id === orderId);
  if (!order) throw new Error('Order not found.');
  if (order.courierId) throw new Error('Another courier already took this delivery.');
  order.courierId = courier.id;
  order.courierName = courier.name;
  syncCourierState(courier.id);
  return copy(order);
}

// TODO: replace with fetch() to the real backend: POST /orders/{id}/transitions  { status, reason? }
export async function updateOrderStatus(orderId: string, status: OrderStatus, rejectReason?: string): Promise<Order> {
  const order = db.orders.find((o) => o.id === orderId);
  if (!order) throw new Error('Order not found.');
  order.status = status;
  if (rejectReason) order.rejectReason = rejectReason.trim();
  if (status === 'picked_up') order.pickedUpAt = new Date().toISOString();
  if (order.courierId) syncCourierState(order.courierId);
  return copy(order);
}
