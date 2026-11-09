// prcApi (a2: ui.prc): every pricing, payment and history call to the backend.
// Each function is one REST call; adapters.ts turns the backend's JSON into the types in domain.ts.
// Money comes from the backend in cents; the adapters turn it into dollars, like every other price on screen.
import type { Checkout, HistoryRow, OrderStatus, OrderSummary } from '../../domain';
import { toCheckout, toHistoryRow, toOrderSummary } from '../adapters';
import { request } from '../http';

/** What the cart sends at checkout and again at placement. */
export interface CartOrder {
  restaurantId: string;
  lines: { itemId: string; quantity: number }[];
  instructions: string;
  deliveryAddress: string;
}

const trimmed = (cart: CartOrder) => ({ ...cart, instructions: cart.instructions.trim(), deliveryAddress: cart.deliveryAddress.trim() });

/**
 * POST /payments/checkout (#259): the backend prices the cart (R-PRC-01) and holds that amount on the card.
 * Answers with the fare and the Stripe client secret the card field confirms against. Nothing is placed yet.
 */
export async function checkout(cart: CartOrder): Promise<Checkout> {
  return toCheckout(await request('/payments/checkout', { method: 'POST', body: trimmed(cart) }));
}

/**
 * POST /payments/place-order (#259): places the order once the card is confirmed, with the same cart plus the payment.
 * The backend refuses (409/400) a closed restaurant, a sold-out item, or a payment that doesn't match the cart.
 */
export async function placePaidOrder(cart: CartOrder, paymentId: string): Promise<{ id: string; status: OrderStatus }> {
  const placed = await request<{ orderId: string; status: OrderStatus }>('/payments/place-order', {
    method: 'POST',
    body: { ...trimmed(cart), paymentId },
  });
  return { id: placed.orderId, status: placed.status };
}

/** GET /orders/{id}/summary (#262): a delivered order's fare, line by line. A customer only gets their own. */
export async function getOrderSummary(orderId: string): Promise<OrderSummary> {
  return toOrderSummary(await request(`/orders/${orderId}/summary`));
}

/**
 * GET /history (#264): the signed-in user's ledger, newest first. Scoped by the session: a customer gets their orders,
 * a restaurant its restaurant's, the operator everything, and a courier the orders they delivered, with no money.
 */
export async function getHistory(): Promise<HistoryRow[]> {
  return (await request<unknown[]>('/history')).map(toHistoryRow);
}
