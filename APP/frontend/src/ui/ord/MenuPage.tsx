import { useState, type CSSProperties } from 'react';
import { Link, useParams } from 'react-router-dom';
import type { MenuItem } from '../../domain';
import { useUser } from '../../session';
import { ConfirmChange, DiscountTile, Empty, LoadError, Loading, money, restaurantHue, stagger, StatusBadge, Switch, useLoad } from '../common';
import { errorMessage, send } from '../dashboard/dashboardApi';
import { Icon } from '../icons';
import OrderCart, { type Cart } from './OrderCart';
import { getRestaurant, listMenu, setMenuItemStatus } from './ordApi';

/**
 * One MenuPage, two views (a2: ui.ord): a customer browses a restaurant's menu and adds items to the cart,
 * a restaurant manages its own items' availability. A restaurant can only ever see its own menu.
 * `onChanged` lets a parent (the restaurant dashboard) refresh its own numbers after a change.
 */
export default function MenuPage({ onChanged }: { onChanged?: () => void }) {
  const { role } = useUser();
  return role === 'restaurant' ? <RestaurantMenu onChanged={onChanged} /> : <CustomerMenu />;
}

function RestaurantMenu({ onChanged }: { onChanged?: () => void }) {
  const restaurantId = useUser().restaurantId ?? '';
  const { data, error, reload } = useLoad(() => Promise.all([getRestaurant(restaurantId), listMenu(restaurantId)]), [restaurantId]);
  const [actionError, setActionError] = useState('');

  async function toggle(item: MenuItem) {
    const status = item.status === 'available' ? 'sold_out' : 'available';
    setActionError('');
    try {
      await send(`Mark "${item.name}" ${status.replace('_', ' ')}`, () => setMenuItemStatus(item.id, status));
    } catch (e) {
      setActionError(errorMessage(e));
    }
    reload();
    onChanged?.();
  }

  if (error) return <LoadError what="your menu" message={error} onRetry={reload} />;
  if (!data) return <Loading what="your menu" />;
  const [restaurant, menu] = data;

  return (
    <section className="card" style={{ '--hue': restaurantHue(restaurant) } as CSSProperties}>
      <div className="page-head">
        <div>
          <h2>Today's menu</h2>
          <p className="muted">Flip items off the moment they run out so nobody orders something you can't serve.</p>
        </div>
      </div>
      {actionError && (
        <p className="alert alert-error" role="alert">
          {actionError}
        </p>
      )}
      {menu.length === 0 ? (
        <Empty icon="utensils" title="No menu items yet" />
      ) : (
        <ul className="menu-admin">
          {menu.map((item) => (
            <li key={item.id} className={item.status === 'sold_out' ? 'off' : ''}>
              <DiscountTile small price={item.price} originalPrice={item.originalPrice} soldOut={item.status === 'sold_out'} />
              <span className="menu-admin-name">
                <strong>{item.name}</strong>
                <small className="muted">
                  {money(item.price)} <s>{money(item.originalPrice)}</s>
                </small>
              </span>
              <StatusBadge status={item.status} />
              <ConfirmChange question={`Mark "${item.name}" ${item.status === 'available' ? 'sold out' : 'available'}?`} onConfirm={() => toggle(item)}>
                {(ask) => <Switch checked={item.status === 'available'} label={`${item.name} available`} onChange={ask} />}
              </ConfirmChange>
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
            <span>{restaurant.cuisine}</span>
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
                        <s className="muted">{money(item.originalPrice)}</s>
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
