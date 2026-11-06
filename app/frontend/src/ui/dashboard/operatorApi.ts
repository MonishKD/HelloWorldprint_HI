// operatorApi (a2: ui.dashboard): every call the operator dashboard makes to the backend.
// Each function is one REST call; adapters.ts turns the backend's JSON into the types in domain.ts.
//
// Most of these routes don't exist yet, so requests go to a fake backend for now.
// To connect the real one, change the import below to `import { request } from '../http';` and delete operatorMock.ts.
// If the routes or JSON end up different: routes are fixed here, field names in adapters.ts.
import type { CityView, Order, RestaurantStatus, StalledOrder, WaitingOrder } from '../../domain';
import { toCityView, toOrder, toStalledOrder, toWaitingOrder } from '../adapters';
import { mockRequest as request } from './operatorMock';

/** GET /dashboard/city (#268): restaurants with their counts, and on-duty couriers. */
export async function getCityView(): Promise<CityView> {
  return toCityView(await request('/dashboard/city'));
}

/** GET /operator/orders (#274): every order. */
export async function listAllOrders(): Promise<Order[]> {
  return (await request<unknown[]>('/operator/orders')).map(toOrder);
}

/** GET /operator/dispatch-queue (#250): ready orders with no courier, oldest first. */
export async function listDispatchQueue(): Promise<WaitingOrder[]> {
  return (await request<unknown[]>('/operator/dispatch-queue')).map(toWaitingOrder);
}

/** GET /operator/stalled-orders (#254) */
export async function listStalledOrders(): Promise<StalledOrder[]> {
  return (await request<unknown[]>('/operator/stalled-orders')).map(toStalledOrder);
}

/** PATCH /restaurants/{id}/status: this route exists, #274 lets the operator use it on any restaurant. */
export async function setRestaurantStatus(restaurantId: string, status: RestaurantStatus): Promise<void> {
  await request(`/restaurants/${restaurantId}/status`, { method: 'PATCH', body: { status } });
}

/** POST /orders/{id}/operator-cancel: this route exists (#239). Refused after pickup. */
export async function cancelOrder(orderId: string): Promise<void> {
  await request(`/orders/${orderId}/operator-cancel`, { method: 'POST' });
}

/** POST /orders/{id}/reassign (#254): refused unless the order is stalled. The dispatch rule then runs again. */
export async function reassignOrder(orderId: string): Promise<void> {
  await request(`/orders/${orderId}/reassign`, { method: 'POST' });
}

/** POST /orders/{id}/refund (#266): refused unless the order is delivered. */
export async function refundOrder(orderId: string): Promise<void> {
  await request(`/orders/${orderId}/refund`, { method: 'POST' });
}

/** POST /operator/reset (#276): everything goes back to the seed. */
export async function resetSystem(): Promise<void> {
  await request('/operator/reset', { method: 'POST' });
}
