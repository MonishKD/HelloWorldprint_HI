import { useState, type FormEvent } from 'react';
import type { MenuItem } from '../../domain';
import type { MenuItemInput } from './ordApi';

/**
 * Add / edit form for one menu item (restaurant view of MenuPage). Nothing is sent until the user presses the
 * submit button, which is this action's confirm step; the parent decides what happens on success or refusal.
 */
export default function MenuItemForm({
  item,
  busy,
  onSubmit,
  onCancel,
}: {
  item?: MenuItem;
  busy?: boolean;
  onSubmit: (input: MenuItemInput) => void;
  onCancel: () => void;
}) {
  const [name, setName] = useState(item?.name ?? '');
  const [description, setDescription] = useState(item?.description ?? '');
  const [originalPrice, setOriginalPrice] = useState(item ? String(item.originalPrice) : '');
  const [price, setPrice] = useState(item ? String(item.price) : '');
  const [available, setAvailable] = useState(item ? item.status === 'available' : true);

  const off = Number(price) > 0 && Number(originalPrice) > Number(price) ? Math.round((1 - Number(price) / Number(originalPrice)) * 100) : 0;

  function submit(e: FormEvent) {
    e.preventDefault();
    // A blank normal price means "no discount": the backend only stores an original price for discounted items.
    onSubmit({ name, description, originalPrice: originalPrice ? Number(originalPrice) : undefined, price: Number(price), status: available ? 'available' : 'sold_out' });
  }

  return (
    <form className="item-form" onSubmit={submit}>
      <label className="field">
        <span>Item name</span>
        <input required autoFocus pattern=".*\S.*" placeholder="e.g. Sourdough loaf" value={name} onChange={(e) => setName(e.target.value)} />
      </label>
      <label className="field">
        <span>Description</span>
        <input placeholder="What's in the bag?" value={description} onChange={(e) => setDescription(e.target.value)} />
      </label>
      <div className="item-form-prices">
        <label className="field">
          <span>Normal price ($, optional)</span>
          <input type="number" inputMode="decimal" min="0.01" step="0.01" value={originalPrice} onChange={(e) => setOriginalPrice(e.target.value)} />
        </label>
        <label className="field">
          <span>Discounted price ($)</span>
          <input required type="number" inputMode="decimal" min="0.01" step="0.01" value={price} onChange={(e) => setPrice(e.target.value)} />
        </label>
      </div>
      {off > 0 && <p className="muted small">Customers will see −{off}% on this item.</p>}
      <label className="check">
        <input type="checkbox" checked={available} onChange={(e) => setAvailable(e.target.checked)} />
        Available now
      </label>
      <div className="order-actions">
        <button type="button" className="btn btn-ghost btn-sm" onClick={onCancel}>
          Cancel
        </button>
        <button className="btn btn-primary btn-sm" disabled={busy}>
          {busy ? 'Saving…' : item ? 'Save changes' : 'Add item'}
        </button>
      </div>
    </form>
  );
}
