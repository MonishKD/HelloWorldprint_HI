import { useState, type CSSProperties } from 'react';
import { Link, useParams } from 'react-router-dom';
import type { MenuItem } from '../../domain';
import { useUser } from '../../session';
import { ConfirmButton, ConfirmChange, DiscountTile, Empty, LoadError, Loading, money, restaurantHue, stagger, StatusBadge, Switch, useLoad } from '../common';
import { errorMessage, send } from '../dashboard/dashboardApi';
import { Icon } from '../icons';
import OrderCart, { type Cart } from './OrderCart';
import MenuItemForm from './MenuItemForm';
import { addMenuItem, getRestaurant, listMenu, removeMenuItem, setMenuItemStatus, updateMenuItem } from './ordApi';

/**
 * One MenuPage, two views (a2: ui.ord): a customer browses a restaurant's menu and adds items to the cart,
 * a restaurant manages its own menu (add, edit, remove, availability). A restaurant can only ever see its own menu.
 * `onChanged` lets a parent (the restaurant dashboard) refresh its own numbers after a change.
 */
export default function MenuPage({ onChanged }: { onChanged?: () => void }) {
  const { role } = useUser();
  const { restaurantId } = useParams();
  // The key resets the cart (items, instructions, address) whenever another restaurant is opened,
  // so nothing typed for one restaurant ends up in an order for another.
  return role === 'restaurant' ? <RestaurantMenu onChanged={onChanged} /> : <CustomerMenu key={restaurantId} />;
}

function RestaurantMenu({ onChanged }: { onChanged?: () => void }) {
  const restaurantId = useUser().restaurantId ?? '';
  const { data, error, reload } = useLoad(() => Promise.all([getRestaurant(restaurantId), listMenu(restaurantId)]), [restaurantId]);
  const [actionError, setActionError] = useState('');
  const [adding, setAdding] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  /** Sends one confirmed change; on success `done` closes the form, on refusal the form stays open so nothing typed is lost. */
  async function run(action: string, call: () => Promise<unknown>, done?: () => void) {
    setActionError('');
    setBusy(true);
    try {
      await send(action, call);
      done?.();
    } catch (e) {
      setActionError(errorMessage(e));
    }
    setBusy(false);
    reload();
    onChanged?.();
  }

  const toggle = (item: MenuItem) => {
    const status = item.status === 'available' ? 'sold_out' : 'available';
    return run(`Mark "${item.name}" ${status.replace('_', ' ')}`, () => setMenuItemStatus(item.id, status));
  };

  if (error) return <LoadError what="your menu" message={error} onRetry={reload} />;
  if (!data) return <Loading what="your menu" />;
  const [restaurant, menu] = data;

  return (
    <section className="card" style={{ '--hue': restaurantHue(restaurant) } as CSSProperties}>
      <div className="page-head">
        <div>
          <h2>Today's menu</h2>
          <p className="muted">Add what you have left, and flip items off the moment they run out so nobody orders something you can't serve.</p>
        </div>
        {!adding && menu.length > 0 && (
          <button className="btn btn-secondary btn-sm" onClick={() => setAdding(true)}>
            + Add item
          </button>
        )}
      </div>
      {actionError && (
        <p className="alert alert-error" role="alert">
          {actionError}
        </p>
      )}
      {adding && (
        <MenuItemForm
          busy={busy}
          onCancel={() => setAdding(false)}
          onSubmit={(input) => run(`Add menu item "${input.name.trim()}"`, () => addMenuItem(restaurantId, input), () => setAdding(false))}
        />
      )}
      {menu.length === 0 ? (
        !adding && (
          <Empty icon="utensils" title="Your menu is empty">
            Add your first item: a name, its normal price and the discounted price. Once it's on the menu, open your restaurant from the status control at the top.
            <button className="btn btn-primary" onClick={() => setAdding(true)}>
              Add your first item
            </button>
          </Empty>
        )
      ) : (
        <ul className="menu-admin">
          {menu.map((item) => (
            <li key={item.id} className={`${item.status === 'sold_out' ? 'off' : ''} ${editingId === item.id ? 'editing' : ''}`}>
              {editingId === item.id ? (
                <MenuItemForm
                  item={item}
                  busy={busy}
                  onCancel={() => setEditingId(null)}
                  onSubmit={(input) => run(`Edit menu item "${item.name}"`, () => updateMenuItem(restaurantId, item.id, input), () => setEditingId(null))}
                />
              ) : (
                <>
                  <DiscountTile small price={item.price} originalPrice={item.originalPrice} soldOut={item.status === 'sold_out'} />
                  <span className="menu-admin-name">
                    <strong>{item.name}</strong>
                    <small className="muted">
                      {money(item.price)} {item.originalPrice > item.price && <s>{money(item.originalPrice)}</s>}
                    </small>
                  </span>
                  <StatusBadge status={item.status} />
                  <span className="menu-admin-actions">
                    <ConfirmChange question={`Mark "${item.name}" ${item.status === 'available' ? 'sold out' : 'available'}?`} onConfirm={() => toggle(item)}>
                      {(ask) => <Switch checked={item.status === 'available'} label={`${item.name} available`} onChange={ask} />}
                    </ConfirmChange>
                    <button className="btn btn-ghost btn-sm" onClick={() => setEditingId(item.id)}>
                      Edit
                    </button>
                    <ConfirmButton
                      variant="danger"
                      label="Remove"
                      question={`Remove "${item.name}" from your menu?`}
                      onConfirm={() => run(`Remove menu item "${item.name}"`, () => removeMenuItem(restaurantId, item.id))}
                    />
                  </span>
                </>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function CustomerMenu() {
  const { restaurantId = '' } = useParams();
  const { data, error, reload } = useLoad(() => Promise.all([getRestaurant(restaurantId), listMenu(restaurantId)]), [restaurantId]);
  const [cart, setCart] = useState<Cart>({});

  const back = (
    <Link to="/customer" className="back">
      ← All restaurants
    </Link>
  );
  if (error) {
    return (
      <section>
        {back}
        <LoadError what="this menu" message={error} onRetry={reload} />
      </section>
    );
  }
  if (!data) return <Loading what="menu" />;

  const [restaurant, menu] = data;
  const isOpen = restaurant.status === 'open';
  const setQuantity = (itemId: string, quantity: number) =>
    setCart(({ [itemId]: _old, ...rest }) => (quantity > 0 ? { ...rest, [itemId]: quantity } : rest));
  const sortedMenu = [...menu].sort((a, b) => Number(a.status === 'sold_out') - Number(b.status === 'sold_out'));

  return (
    <section style={{ '--hue': restaurantHue(restaurant) } as CSSProperties}>
      {back}
      <header className="store-head rise">
        <div className="store-cover">
          <span className="store-letter" aria-hidden="true">
            {restaurant.name[0]}
          </span>
        </div>
        <div className="store-info">
          <div className="store-title">
            <h1>{restaurant.name}</h1>
            <StatusBadge status={restaurant.status} />
          </div>
          <p className="store-meta">
            {restaurant.rating && (
              <span className="rating">
                <Icon name="star" size={12} filled /> {restaurant.rating.toFixed(1)}
              </span>
            )}
            <span>{restaurant.cuisine || 'New on FortunateGoods'}</span>
            {restaurant.distanceKm !== undefined && <span>{restaurant.distanceKm} km</span>}
            {restaurant.address && <span>{restaurant.address}</span>}
          </p>
          {restaurant.pickupWindow && (
            <span className="pickup">
              <Icon name="clock" size={14} /> Collect {restaurant.pickupWindow}
            </span>
          )}
        </div>
      </header>

      {!isOpen && (
        <p className="alert alert-warn banner">
          <strong>{restaurant.name} is {restaurant.status} right now.</strong> You can browse the menu, but ordering is unavailable.
        </p>
      )}

      <div className="menu-layout">
        <div>
          <h2 className="menu-heading">Today's surplus</h2>
          {menu.length === 0 ? (
            <Empty icon="utensils" title="Nothing listed yet">
              This restaurant hasn't added any items.
            </Empty>
          ) : (
            <ul className="menu-list">
              {sortedMenu.map((item, i) => {
                const soldOut = item.status === 'sold_out';
                const inCart = cart[item.id] ?? 0;
                return (
                  <li key={item.id} className={`menu-item rise ${soldOut ? 'sold-out' : ''}`} style={stagger(i)}>
                    <div className="menu-text">
                      <h3>{item.name}</h3>
                      <p className="muted">{item.description}</p>
                      <div className="price-row">
                        <span className="price">{money(item.price)}</span>
                        {item.originalPrice > item.price && <s className="muted">{money(item.originalPrice)}</s>}
                      </div>
                    </div>
                    <div className="menu-thumb">
                      <DiscountTile price={item.price} originalPrice={item.originalPrice} soldOut={soldOut} />
                      <button
                        className={`add-btn ${inCart ? 'has' : ''}`}
                        aria-label={`Add ${item.name} to cart`}
                        disabled={!isOpen || soldOut}
                        onClick={() => setQuantity(item.id, inCart + 1)}
                      >
                        {inCart || '+'}
                      </button>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
        <OrderCart restaurant={restaurant} menu={menu} cart={cart} onQuantityChange={setQuantity} />
      </div>
    </section>
  );
}
