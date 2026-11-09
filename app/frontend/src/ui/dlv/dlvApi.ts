// dlvApi (a2: ui.dlv): every courier call to the backend.
// Each function is one REST call; adapters.ts turns the backend's JSON into the types in domain.ts.
// Accept and decline answer once #248 lands; until then they fail and the error shows in the output console.
import type { CourierDashboard, ReadyOrder } from '../../domain';
import { toCourierDashboard, toReadyOrder } from '../adapters';
import { request } from '../http';

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

/** GET /couriers/ready-orders (#245): every restaurant's ready orders, oldest first, read-only. */
export async function listReadyOrders(): Promise<ReadyOrder[]> {
  return (await request<unknown[]>('/couriers/ready-orders')).map(toReadyOrder);
}
