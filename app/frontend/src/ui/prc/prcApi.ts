// prcApi (a2: ui.prc): every pricing, payment and history call to the backend.
// Each function is one REST call; adapters.ts turns the backend's JSON into the types in domain.ts.
// Money comes from the backend in cents; the adapters turn it into dollars, like every other price on screen.
import type { Checkout, HistoryRow, LedgerRow, OrderStatus, OrderSummary } from '../../domain';
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

/** One order's card payment as Stripe saw it: the summary (card, amount, times) and the refund ledger row, if any. */
export interface PaymentTrail {
  summary: OrderSummary;
  refund?: LedgerRow;
  /** The Stripe PaymentIntent, for the operator's link to the Stripe dashboard. */
  paymentId?: string;
}

/**
 * GET /orders/{id}/summary + GET /history: the summary has the card and the charge, the ledger has the refund
 * (amount and time). Customers and the operator only (the summary refuses the other roles).
 * TODO(backend): LedgerHistoryResponse does not send the ledger's `paymentReference` yet. Once it does, paymentId fills
 * in and the operator gets the "View in Stripe" link; nothing else here changes.
 * ponytail: the operator's /history is every order, fine at demo size; a per-order ledger endpoint if it ever grows.
 */
export async function getPaymentTrail(orderId: string): Promise<PaymentTrail> {
  const [summary, history] = await Promise.all([getOrderSummary(orderId), getHistory()]);
  const rows = history.filter((r): r is LedgerRow => 'entryType' in r && r.orderId === orderId);
  return { summary, refund: rows.find((r) => r.entryType === 'refund'), paymentId: rows.find((r) => r.paymentId)?.paymentId };
}
