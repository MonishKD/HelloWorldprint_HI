import { useState, type CSSProperties } from 'react';
import type { RestaurantStatus } from '../../domain';
import { useUser } from '../../session';
import { LoadError, Loading, money, Monogram, restaurantHue, Segmented, Stat, TERMINAL, useLoad } from '../common';
import IncomingOrdersList from '../ord/IncomingOrdersList';
import MenuPage from '../ord/MenuPage';
import { getRestaurant, listMenu, listRestaurantOrders, setRestaurantStatus } from '../ord/ordApi';
import { errorMessage, getPreparationQueue, send } from './dashboardApi';
import { DashboardLayout } from './DashboardRouter';

export default function RestaurantDashboardPage() {
  return (
    <DashboardLayout>
      <RestaurantDashboard />
    </DashboardLayout>
  );
}

function RestaurantDashboard() {
  const user = useUser();
  const restaurantId = user.restaurantId ?? '';
  const { data, error, reload } = useLoad(
    () => Promise.all([getRestaurant(restaurantId), listMenu(restaurantId), listRestaurantOrders(restaurantId), getPreparationQueue(restaurantId)]),
    [restaurantId],
  );
  const [actionError, setActionError] = useState('');

  async function run(action: string, call: () => Promise<unknown>) {
    setActionError('');
    try {
      await send(action, call);
    } catch (e) {
      setActionError(errorMessage(e));
    }
    reload();
  }

  const changeStoreStatus = (status: RestaurantStatus) => run(`Set store status to ${status}`, () => setRestaurantStatus(restaurantId, status));

  if (error) return <LoadError what="your restaurant" message={error} onRetry={reload} />;
  if (!data) return <Loading what="your restaurant" />;

  const [restaurant, menu, orders, queue] = data;
  const byArrival = [...orders].sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  const incoming = byArrival.filter((o) => o.status === 'placed');
  // The preparation queue (R-ORD-03) arrives already ordered by the backend; it is shown exactly as received.
  const kitchen = queue;
  const ready = byArrival.filter((o) => o.status === 'ready');
  const history = byArrival.filter((o) => o.status === 'picked_up' || TERMINAL.includes(o.status)).reverse();
  const earning = orders.filter((o) => !['rejected', 'cancelled', 'refunded'].includes(o.status));
  const available = menu.filter((m) => m.status === 'available').length;

  return (
    <>
      <header className="store-banner rise" style={{ '--hue': restaurantHue(restaurant) } as CSSProperties}>
        <Monogram name={restaurant.name} hue={restaurantHue(restaurant)} size="lg" />
        <div className="store-banner-text">
          <span className="eyebrow">Restaurant dashboard</span>
          <h1>{restaurant.name}</h1>
          <p className="muted">{restaurant.address || restaurant.cuisine}</p>
        </div>
        <div className="store-banner-actions">
          <Segmented<RestaurantStatus>
            label="Store status"
            value={restaurant.status}
            options={['open', 'paused', 'closed']}
            onChange={changeStoreStatus}
            confirmText={(status) => `Set your store to ${status}?`}
          />
          <button className="btn btn-ghost btn-sm" onClick={reload}>
            ↻ Refresh
          </button>
        </div>
      </header>

      {actionError && (
        <p className="alert alert-error" role="alert">
          {actionError}
        </p>
      )}

      <div className="stats">
        <Stat label="New orders" value={incoming.length} hint={incoming.length ? 'Waiting for you' : 'All caught up'} tone={incoming.length ? 'accent' : undefined} />
        <Stat label="In preparation" value={kitchen.length} />
        <Stat label="Ready for pickup" value={ready.length} />
        <Stat label="Sales" value={money(earning.reduce((n, o) => n + o.total, 0))} hint={`${earning.length} orders`} tone="brand" />
        <Stat label="On the menu" value={`${available}/${menu.length}`} hint="items available" />
      </div>

      <div className="board">
        <section className="board-col">
          <h2 className="section-title">
            New <span className="count">{incoming.length}</span>
          </h2>
          <IncomingOrdersList orders={incoming} emptyIcon="bell" emptyText="No new orders right now" onChanged={reload} />
        </section>
        <section className="board-col">
          <h2 className="section-title">
            Preparation queue <span className="count">{kitchen.length}</span>
            <small className="muted">oldest first</small>
          </h2>
          <IncomingOrdersList orders={kitchen} emptyIcon="pot" emptyText="Nothing to prepare" onChanged={reload} />
        </section>
        <section className="board-col">
          <h2 className="section-title">
            Ready for pickup <span className="count">{ready.length}</span>
          </h2>
          <IncomingOrdersList orders={ready} emptyIcon="box" emptyText="No bags waiting" onChanged={reload} />
        </section>
      </div>

      <MenuPage onChanged={reload} />

      <details className="history">
        <summary>
          Order history <span className="count">{history.length}</span>
        </summary>
        <IncomingOrdersList orders={history} emptyIcon="inbox" emptyText="No finished orders yet" onChanged={reload} />
      </details>
    </>
  );
}
