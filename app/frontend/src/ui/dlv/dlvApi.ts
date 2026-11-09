// dlvApi (a2: ui.dlv): every courier call to the backend.
// Each function is one REST call; adapters.ts turns the backend's JSON into the types in domain.ts.
//
// The courier routes don't exist yet (#245, #248, #252), so requests go to a fake backend for now.
// To connect the real one, change the import below to `import { request } from '../http';` and delete dlvMock.ts.
// If the routes or JSON end up different: routes are fixed here, field names in adapters.ts.
import type { CourierDashboard, ReadyOrder } from '../../domain';
import { toCourierDashboard, toReadyOrder } from '../adapters';
import { mockRequest as request } from './dlvMock';

/** GET /couriers/me: the courier's state, current offer and current order (#245). */
export async function getCourierDashboard(): Promise<CourierDashboard> {
  return toCourierDashboard(await request('/couriers/me'));
}

/** POST /couriers/me/on-duty and /off-duty. Going off duty is refused while carrying (R-DLV-03). */
export async function setOnDuty(onDuty: boolean): Promise<void> {
  await request(`/couriers/me/${onDuty ? 'on-duty' : 'off-duty'}`, { method: 'POST' });
}

/** POST /couriers/me/offer/accept (#248) */
export async function acceptOffer(): Promise<void> {
  await request('/couriers/me/offer/accept', { method: 'POST' });
}

/** POST /couriers/me/offer/decline (#248): the order goes back to the dispatch queue, and may be offered again. */
export async function declineOffer(): Promise<void> {
  await request('/couriers/me/offer/decline', { method: 'POST' });
}

/** POST /couriers/me/order/pickup (#252): refused before the order is ready (R-ORD-04). */
export async function confirmPickup(): Promise<void> {
  await request('/couriers/me/order/pickup', { method: 'POST' });
}

/** POST /couriers/me/order/deliver (#252): refused before pickup (R-ORD-04). */
export async function confirmDelivery(): Promise<void> {
  await request('/couriers/me/order/deliver', { method: 'POST' });
}

/**
 * GET /couriers/ready-orders: every restaurant's ready orders, oldest first, read-only.
 * No issue covers this route yet: the backend needs to add it, with no customer or payment fields.
 */
export async function listReadyOrders(): Promise<ReadyOrder[]> {
  return (await request<unknown[]>('/couriers/ready-orders')).map(toReadyOrder);
}
