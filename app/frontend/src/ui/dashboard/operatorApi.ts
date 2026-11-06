// operatorApi (a2: ui.dashboard): every call the operator dashboard makes to the backend.
// Each function is one REST call; adapters.ts turns the backend's JSON into the types in domain.ts.
import type { Order, StalledOrder, WaitingOrder } from '../../domain';
import { toOrder, toStalledOrder, toWaitingOrder } from '../adapters';
import { request } from '../http';

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
