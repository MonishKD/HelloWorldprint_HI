import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { LoadError, money, StatusBadge, useLoad } from '../common';
import OrderCart, { type Cart } from './OrderCart';
import { getRestaurant, listMenu } from './ordApi';

export default function MenuPage() {
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
  if (!data) return <p className="muted">Loading menu…</p>;

  const [restaurant, menu] = data;
  const isOpen = restaurant.status === 'open';
  const setQuantity = (itemId: string, quantity: number) =>
    setCart(({ [itemId]: _old, ...rest }) => (quantity > 0 ? { ...rest, [itemId]: quantity } : rest));

  return (
    <section>
      {back}
      <div className="section-head">
        <div>
          <h1>{restaurant.name}</h1>
          <p className="muted">
            {restaurant.cuisine}
            {restaurant.address && ` · ${restaurant.address}`}
          </p>
        </div>
        <StatusBadge status={restaurant.status} />
      </div>
      {!isOpen && (
        <p className="alert alert-warn" style={{ marginBottom: '1rem' }}>
          {restaurant.name} is {restaurant.status} right now. You can browse the menu, but ordering is unavailable.
        </p>
      )}

      <div className="menu-layout">
        <div className="grid">
          {menu.length === 0 ? (
            <p className="empty">This restaurant hasn't listed any items yet.</p>
          ) : (
            menu.map((item) => {
              const soldOut = item.status === 'sold_out';
              const saving = Math.round((1 - item.price / item.originalPrice) * 100);
              return (
                <article key={item.id} className={`card menu-item ${soldOut ? 'sold-out' : ''}`}>
                  <div className="order-head">
                    <h3>{item.name}</h3>
                    <StatusBadge status={item.status} />
                  </div>
                  <p className="muted">{item.description}</p>
                  <div className="row">
                    <span className="price">
                      {money(item.price)}
                      <s>{money(item.originalPrice)}</s>
                      {saving > 0 && <span className="save">Save {saving}%</span>}
                    </span>
                    <button
                      className="btn btn-secondary btn-sm"
                      disabled={!isOpen || soldOut}
                      onClick={() => setQuantity(item.id, (cart[item.id] ?? 0) + 1)}
                    >
                      {cart[item.id] ? `Add (${cart[item.id]})` : 'Add'}
                    </button>
                  </div>
                </article>
              );
            })
          )}
        </div>
        <OrderCart restaurant={restaurant} menu={menu} cart={cart} onQuantityChange={setQuantity} />
      </div>
    </section>
  );
}
