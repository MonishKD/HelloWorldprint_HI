import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { Checkout, MenuItem, OrderLine, Restaurant } from '../../domain';
import { useUser } from '../../session';
import { money, useLoad } from '../common';
import { errorMessage, getDashboardOrders, send } from '../dashboard/dashboardApi';
import { Icon } from '../icons';
import CardPayment, { type ConfirmCard } from '../prc/CardPayment';
import FareLines from '../prc/FareLines';
import { checkout, placePaidOrder, type CartOrder } from '../prc/prcApi';

/** Menu item id → quantity. */
export type Cart = Record<string, number>;

export default function OrderCart({
  restaurant,
  menu,
  cart,
  onQuantityChange,
}: {
  restaurant: Restaurant;
  menu: MenuItem[];
  cart: Cart;
  onQuantityChange: (itemId: string, quantity: number) => void;
}) {
  const user = useUser();
  const navigate = useNavigate();
  const panelRef = useRef<HTMLElement>(null);
  const [address, setAddress] = useState(user.address ?? '');
  // The backend delivers to the customer's saved address (#233) and copies it, with its coordinates, onto each order.
  // No route returns that address yet, so it is read from the newest such order, read-only. A customer with no saved
  // location (or no order yet) types one, which the backend uses when there is no saved location.
  // ponytail: add the saved address to GET /auth/session and read it from there once the backend has it.
  const { data: pastOrders, error: pastOrdersError } = useLoad(getDashboardOrders, []);
  const savedAddress = pastOrders && [...pastOrders].reverse().find((o) => o.deliveryLat !== undefined)?.deliveryAddress;
  const addressLoading = !pastOrders && !pastOrdersError;
  const deliveryAddress = savedAddress ?? address;
  const [instructions, setInstructions] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  // The amount held at checkout belongs to one cart: change a quantity and it no longer applies.
  const [payment, setPayment] = useState<{ cart: string; checkout: Checkout } | null>(null);

  const lines: OrderLine[] = menu
    .filter((m) => cart[m.id])
    .map((m) => ({ itemId: m.id, name: m.name, price: m.price, originalPrice: m.originalPrice, quantity: cart[m.id] }));
  const count = lines.reduce((n, l) => n + l.quantity, 0);
  const total = lines.reduce((sum, l) => sum + l.price * l.quantity, 0);
  const saved = lines.reduce((sum, l) => sum + (l.originalPrice - l.price) * l.quantity, 0);
  const cartKey = JSON.stringify(lines.map((l) => [l.itemId, l.quantity]));
  const held = payment?.cart === cartKey ? payment.checkout : null;
  const order: CartOrder = { restaurantId: restaurant.id, lines: lines.map((l) => ({ itemId: l.itemId, quantity: l.quantity })), instructions, deliveryAddress };

  // UI-level checks only; the backend will own the real ordering rules.
  const blockedReason =
    restaurant.status !== 'open'
      ? `Ordering is unavailable while ${restaurant.name} is ${restaurant.status}.`
      : menu.length === 0
        ? `${restaurant.name} hasn't added any items yet.`
        : menu.every((m) => m.status === 'sold_out')
          ? 'Everything is sold out right now. Check back later!'
          : '';

  const cannotOrder = busy || !!blockedReason || lines.length === 0 || !deliveryAddress.trim();

  /** Step 1 (#259): the backend prices the cart and holds that amount; the fare breakdown and the card field appear. */
  async function startCheckout() {
    setBusy(true);
    setError('');
    try {
      setPayment({ cart: cartKey, checkout: await send(`Check out at ${restaurant.name}`, () => checkout(order)) });
    } catch (e) {
      setError(errorMessage(e));
    }
    setBusy(false);
  }

  /** Step 2: confirm the card, then place the paid order. A declined card places nothing and keeps the cart. */
  async function pay(confirmCard: ConfirmCard) {
    if (!held) return;
    setBusy(true);
    setError('');
    try {
      const placed = await send(`Place order at ${restaurant.name} (${money(held.fare.total)})`, async () => {
        await confirmCard();
        return placePaidOrder(order, held.paymentId);
      });
      navigate('/customer/orders', { state: { placed: { id: placed.id, status: placed.status } } });
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  }

  return (
    <>
      <aside className="cart card" aria-label="Your cart" ref={panelRef}>
        <div className="cart-head">
          <h2>Your cart</h2>
          {count > 0 && <span className="muted">{count} item{count > 1 ? 's' : ''}</span>}
        </div>

        {lines.length === 0 ? (
          <div className="cart-empty">
            <span className="cart-empty-icon">
              <Icon name="bag" size={22} />
            </span>
            <p className="muted">Your cart is empty. Add something tasty before it's gone!</p>
          </div>
        ) : (
          <ul className="cart-lines">
            {lines.map((l) => (
              <li key={l.itemId} className="cart-line">
                <span className="stepper">
                  <button aria-label={`One less ${l.name}`} onClick={() => onQuantityChange(l.itemId, l.quantity - 1)}>
                    {l.quantity === 1 ? <Icon name="trash" size={14} /> : '−'}
                  </button>
                  <span>{l.quantity}</span>
                  <button aria-label={`One more ${l.name}`} onClick={() => onQuantityChange(l.itemId, l.quantity + 1)}>
                    +
                  </button>
                </span>
                <span className="cart-name">{l.name}</span>
                <span>{money(l.price * l.quantity)}</span>
              </li>
            ))}
          </ul>
        )}

        <label className="field">
          <span>Deliver to</span>
          {savedAddress || addressLoading ? (
            <input readOnly value={savedAddress ?? ''} placeholder="Loading your saved address…" />
          ) : (
            <input required placeholder="Street address, apt" autoComplete="street-address" value={address} onChange={(e) => setAddress(e.target.value)} />
          )}
          {savedAddress && <small className="muted">Your saved address: every order goes here.</small>}
        </label>
        <label className="field">
          <span>Special instructions (optional)</span>
          <textarea placeholder="Allergies, buzzer code, drop-off notes…" maxLength={300} value={instructions} onChange={(e) => setInstructions(e.target.value)} />
        </label>

        {saved > 0 && (
          <div className="saved-row">
            <span>You're saving</span>
            <span>{money(saved)}</span>
          </div>
        )}
        {held ? (
          <FareLines fare={held.fare} />
        ) : (
          <div className="totals">
            <div className="total">
              <span>Items</span>
              <span>{money(total)}</span>
            </div>
            {lines.length > 0 && <p className="muted small">Delivery and service fees are added at checkout.</p>}
          </div>
        )}

        {blockedReason && <p className="alert alert-warn">{blockedReason}</p>}
        {error && (
          <p className="alert alert-error" role="alert">
            {error}
          </p>
        )}

        {held ? (
          <CardPayment
            clientSecret={held.clientSecret}
            label={busy ? 'Placing order…' : `Place order · ${money(held.fare.total)}`}
            question={`Pay ${money(held.fare.total)} and place this order?`}
            disabled={cannotOrder}
            onPay={pay}
          />
        ) : (
          <button type="button" className="btn btn-dark btn-block" disabled={cannotOrder} onClick={startCheckout}>
            {busy ? 'Checking out…' : 'Continue to payment'}
          </button>
        )}
        {lines.length > 0 && !deliveryAddress.trim() && !addressLoading && <p className="muted small center">Add a delivery address to continue.</p>}
      </aside>

      {count > 0 && (
        <button className="cart-fab" onClick={() => panelRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })}>
          <span className="cart-fab-count">{count}</span>
          View cart
          <span>{money(total)}</span>
        </button>
      )}
    </>
  );
}
