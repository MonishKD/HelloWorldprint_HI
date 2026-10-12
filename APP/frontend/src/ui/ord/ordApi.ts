import { db, type MenuItem, type MenuItemStatus, type Order, type OrderLine, type OrderStatus, type Restaurant } from '../../mockData';

export type RestaurantSummary = Restaurant & { itemCount: number; availableCount: number };

// Callers get copies so React state never aliases the in-memory db.
const copy = <T>(value: T): T => structuredClone(value);

// TODO: replace with fetch() to the real backend
export async function listRestaurants(): Promise<RestaurantSummary[]> {
  return db.restaurants.map((r) => {
    const items = db.menuItems.filter((m) => m.restaurantId === r.id);
    return { ...copy(r), itemCount: items.length, availableCount: items.filter((m) => m.status === 'available').length };
  });
}

// TODO: replace with fetch() to the real backend
export async function getRestaurant(id: string): Promise<Restaurant> {
  const restaurant = db.restaurants.find((r) => r.id === id);
  if (!restaurant) throw new Error('Restaurant not found.');
  return copy(restaurant);
}

// TODO: replace with fetch() to the real backend
export async function listMenu(restaurantId: string): Promise<MenuItem[]> {
  return copy(db.menuItems.filter((m) => m.restaurantId === restaurantId));
}

// TODO: replace with fetch() to the real backend
export async function setMenuItemStatus(itemId: string, status: MenuItemStatus): Promise<MenuItem> {
  const item = db.menuItems.find((m) => m.id === itemId);
  if (!item) throw new Error('Menu item not found.');
  item.status = status;
  return copy(item);
}

// TODO: replace with fetch() to the real backend
export async function placeOrder(input: {
  customerId: string;
  customerName: string;
  restaurant: Restaurant;
  lines: OrderLine[];
  specialInstructions: string;
}): Promise<Order> {
  const order: Order = {
    id: String(1001 + db.orders.length),
    customerId: input.customerId,
    customerName: input.customerName,
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

// TODO: replace with fetch() to the real backend
export async function listCustomerOrders(customerId: string): Promise<Order[]> {
  return copy(db.orders.filter((o) => o.customerId === customerId));
}

// TODO: replace with fetch() to the real backend
export async function listRestaurantOrders(restaurantId: string): Promise<Order[]> {
  return copy(db.orders.filter((o) => o.restaurantId === restaurantId));
}

// TODO: replace with fetch() to the real backend
export async function updateOrderStatus(orderId: string, status: OrderStatus, rejectReason?: string): Promise<Order> {
  const order = db.orders.find((o) => o.id === orderId);
  if (!order) throw new Error('Order not found.');
  order.status = status;
  if (rejectReason) order.rejectReason = rejectReason.trim();
  return copy(order);
}
