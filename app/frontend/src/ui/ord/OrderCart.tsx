import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { MenuItem, OrderLine, Restaurant } from '../../domain';
import { useUser } from '../../session';
import { ConfirmButton, money } from '../common';
import { errorMessage, send } from '../dashboard/dashboardApi';
import { Icon } from '../icons';
import { placeOrder } from './ordApi';

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
  const [instructions, setInstructions] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const lines: OrderLine[] = menu
    .filter((m) => cart[m.id])
    .map((m) => ({ itemId: m.id, name: m.name, price: m.price, originalPrice: m.originalPrice, quantity: cart[m.id] }));
  const count = lines.reduce((n, l) => n + l.quantity, 0);
  const total = lines.reduce((sum, l) => sum + l.price * l.quantity, 0);
  const saved = lines.reduce((sum, l) => sum + (l.originalPrice - l.price) * l.quantity, 0);

  // UI-level checks only; the backend will own the real ordering rules.
  const blockedReason =
    restaurant.status !== 'open'
      ? `Ordering is unavailable while ${restaurant.name} is ${restaurant.status}.`
      : menu.length === 0
        ? `${restaurant.name} hasn't added any items yet.`
        : menu.every((m) => m.status === 'sold_out')
          ? 'Everything is sold out right now. Check back later!'
          : '';

  async function confirmOrder() {
    setBusy(true);
    setError('');
    try {
      const order = await send(`Place order at ${restaurant.name} (${money(total)})`, () =>
        placeOrder({
          restaurantId: restaurant.id,
          lines: lines.map((l) => ({ itemId: l.itemId, quantity: l.quantity })),
          instructions,
          deliveryAddress: address,
        }),
      );
      navigate('/customer/orders', { state: { placed: { id: order.id, status: order.status } } });
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
          <input required placeholder="Street address, apt" autoComplete="street-address" value={address} onChange={(e) => setAddress(e.target.value)} />
        </label>
        <label className="field">
          <span>Special instructions (optional)</span>
          <textarea placeholder="Allergies, buzzer code, drop-off notes…" maxLength={300} value={instructions} onChange={(e) => setInstructions(e.target.value)} />
        </label>

        <div className="totals">
          {saved > 0 && (
            <div className="saved-row">
              <span>You're saving</span>
              <span>{money(saved)}</span>
            </div>
          )}
          <div className="total">
            <span>Total</span>
            <span>{money(total)}</span>
          </div>
        </div>

        {blockedReason && <p className="alert alert-warn">{blockedReason}</p>}
        {error && (
          <p className="alert alert-error" role="alert">
            {error}
          </p>
        )}

        <ConfirmButton
          block
          variant="dark"
          label={busy ? 'Placing order…' : `Place order · ${money(total)}`}
          question={`Place this order for ${money(total)}?`}
          disabled={busy || !!blockedReason || lines.length === 0 || !address.trim()}
          onConfirm={confirmOrder}
        />
        {lines.length > 0 && !address.trim() && <p className="muted small center">Add a delivery address to continue.</p>}
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
