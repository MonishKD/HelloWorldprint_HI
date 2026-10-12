import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { MenuItem, OrderLine, Restaurant } from '../../mockData';
import { useUser } from '../../session';
import { ConfirmButton, money } from '../common';
import { errorMessage, send } from '../dashboard/dashboardApi';
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
  const [instructions, setInstructions] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const lines: OrderLine[] = menu
    .filter((m) => cart[m.id])
    .map((m) => ({ itemId: m.id, name: m.name, price: m.price, quantity: cart[m.id] }));
  const total = lines.reduce((sum, l) => sum + l.price * l.quantity, 0);

  // UI-level checks only; the backend will own the real ordering rules.
  const blockedReason =
    restaurant.status !== 'open'
      ? `Ordering is unavailable while ${restaurant.name} is ${restaurant.status}.`
      : menu.every((m) => m.status === 'sold_out')
        ? 'Everything is sold out right now. Check back later!'
        : '';

  async function confirmOrder() {
    setBusy(true);
    setError('');
    try {
      const order = await send(`Place order at ${restaurant.name} (${money(total)})`, () =>
        placeOrder({ customerId: user.id, customerName: user.name, restaurant, lines, specialInstructions: instructions }),
      );
      navigate('/customer/orders', { state: { placedOrderId: order.id } });
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  }

  return (
    <aside className="card cart" aria-label="Your cart">
      <h2>Your cart</h2>
      {lines.length === 0 ? (
        <p className="muted">Your cart is empty. Add something tasty!</p>
      ) : (
        <ul className="cart-lines">
          {lines.map((l) => (
            <li key={l.itemId} className="cart-line">
              <span>{l.name}</span>
              <span>{money(l.price * l.quantity)}</span>
              <span className="qty">
                <button className="btn btn-ghost" aria-label={`One less ${l.name}`} onClick={() => onQuantityChange(l.itemId, l.quantity - 1)}>
                  −
                </button>
                {l.quantity}
                <button className="btn btn-ghost" aria-label={`One more ${l.name}`} onClick={() => onQuantityChange(l.itemId, l.quantity + 1)}>
                  +
                </button>
              </span>
            </li>
          ))}
        </ul>
      )}

      <label>
        Special instructions
        <textarea
          placeholder="Allergies, pickup notes…"
          maxLength={300}
          value={instructions}
          onChange={(e) => setInstructions(e.target.value)}
        />
      </label>

      <div className="total">
        <span>Total</span>
        <span>{money(total)}</span>
      </div>

      {blockedReason && <p className="alert alert-warn">{blockedReason}</p>}
      {error && (
        <p className="alert alert-error" role="alert">
          {error}
        </p>
      )}

      <ConfirmButton
        label={busy ? 'Placing order…' : 'Place order'}
        question={`Place this order for ${money(total)}?`}
        disabled={busy || !!blockedReason || lines.length === 0}
        onConfirm={confirmOrder}
      />
    </aside>
  );
}
