// ordApi (a2: ui.ord): every restaurant, menu and order call to the backend.
// Each function is one REST call; adapters.ts turns the backend's JSON into the types in domain.ts.
import type { MenuItem, MenuItemStatus, OrderStatus, Restaurant, RestaurantStatus } from '../../domain';
import { toMenuItem, toMenuItems, toRestaurant } from '../adapters';
import { request } from '../http';

export type RestaurantSummary = Restaurant & { itemCount: number; availableCount: number; bestDiscount: number };

/** GET /restaurants: every restaurant with its status and position. */
export async function listRestaurants(): Promise<Restaurant[]> {
  return (await request<unknown[]>('/restaurants')).map(toRestaurant);
}

/**
 * The discover page also shows how much each restaurant has left and its best discount, which the list doesn't carry,
 * so each menu is read too.
 */
// ponytail: one menu request per restaurant; ask the backend for counts in GET /restaurants if the list grows
export async function listRestaurantSummaries(): Promise<RestaurantSummary[]> {
  const restaurants = await listRestaurants();
  return Promise.all(
    restaurants.map(async (r) => {
      const [, items] = await getRestaurantMenu(r.id);
      const bestDiscount = Math.max(0, ...items.map((m) => Math.round((1 - m.price / m.originalPrice) * 100)));
      return { ...r, itemCount: items.length, availableCount: items.filter((m) => m.status === 'available').length, bestDiscount };
    }),
  );
}

/** GET /restaurants/{id}/menu: the restaurant's name and status, and its menu. */
export async function getRestaurantMenu(restaurantId: string): Promise<[Restaurant, MenuItem[]]> {
  const menu = await request<{ restaurantId: string; restaurantName: string; restaurantStatus: string }>(`/restaurants/${restaurantId}/menu`);
  return [toRestaurant({ id: menu.restaurantId, name: menu.restaurantName, status: menu.restaurantStatus }), toMenuItems(menu)];
}

/** PATCH /restaurants/{id}/status. The backend refuses opening a restaurant with no menu items. */
export async function setRestaurantStatus(restaurantId: string, status: RestaurantStatus): Promise<void> {
  await request(`/restaurants/${restaurantId}/status`, { method: 'PATCH', body: { status } });
}

/** `originalPrice` is only set for discounted items. */
export type MenuItemInput = Pick<MenuItem, 'name' | 'description' | 'price' | 'status'> & { originalPrice?: number };

/** POST /restaurants/{restaurantId}/menu. The backend refuses invalid items (400) with a reason. */
export async function addMenuItem(restaurantId: string, input: MenuItemInput): Promise<MenuItem> {
  return toMenuItem(await request(`/restaurants/${restaurantId}/menu`, { method: 'POST', body: input }));
}

/** PATCH /restaurants/{restaurantId}/menu/{itemId} */
export async function updateMenuItem(restaurantId: string, itemId: string, input: MenuItemInput): Promise<MenuItem> {
  return toMenuItem(await request(`/restaurants/${restaurantId}/menu/${itemId}`, { method: 'PATCH', body: input }));
}

/** DELETE /restaurants/{restaurantId}/menu/{itemId}. Past orders keep their own copy of the item. */
export async function removeMenuItem(restaurantId: string, itemId: string): Promise<void> {
  await request(`/restaurants/${restaurantId}/menu/${itemId}`, { method: 'DELETE' });
}

/** PATCH /restaurants/{restaurantId}/menu/{itemId}/availability */
export async function setMenuItemStatus(restaurantId: string, itemId: string, status: MenuItemStatus): Promise<void> {
  await request(`/restaurants/${restaurantId}/menu/${itemId}/availability`, { method: 'PATCH', body: { status } });
}

/** POST /orders/{id}/cancel. The backend refuses (409) unless the order is still `placed`. */
export async function cancelOrder(orderId: string): Promise<void> {
  await request(`/orders/${orderId}/cancel`, { method: 'POST' });
}

/** The restaurant's moves, one endpoint each. The backend refuses (409) a move the order's state doesn't allow. */
const MOVES: Partial<Record<OrderStatus, string>> = { accepted: 'accept', rejected: 'reject', preparing: 'prepare', ready: 'ready' };

/** POST /orders/{id}/accept | reject { reason } | prepare | ready */
export async function updateOrderStatus(orderId: string, status: OrderStatus, rejectReason?: string): Promise<void> {
  const move = MOVES[status];
  if (!move) throw new Error(`Moving an order to "${status}" isn't available yet.`);
  await request(`/orders/${orderId}/${move}`, { method: 'POST', body: status === 'rejected' ? { reason: rejectReason } : undefined });
}
