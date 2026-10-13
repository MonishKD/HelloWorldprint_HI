import { useState } from 'react';
import { Link, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import type { Order, Restaurant } from '../../domain';
import { useUser } from '../../session';
import { ConfirmButton, Empty, LoadError, Loading, OrderCard, restaurantHue, stagger, TERMINAL, useLoad } from '../common';
import MenuPage from '../ord/MenuPage';
import MapView from '../map/MapView';
import { deliveryProgress, etaMinutes, useNow } from '../map/geo';
import { listRestaurants, updateOrderStatus } from '../ord/ordApi';
import RestaurantListPage from '../ord/RestaurantListPage';
import { errorMessage, getCustomerOrderStatuses, send } from './dashboardApi';
import { Icon } from '../icons';
import { DashboardLayout, type NavItem } from './DashboardRouter';

const NAV: NavItem[] = [
  { to: '/customer', label: 'Discover', activeFor: (path) => !path.startsWith('/customer/orders') },
  { to: '/customer/orders', label: 'My orders' },
];

export default function CustomerDashboardPage() {
  return (
    <DashboardLayout nav={NAV}>
      <Routes>
        <Route index element={<RestaurantListPage />} />
        <Route path="r/:restaurantId" element={<MenuPage />} />
        <Route path="orders" element={<MyOrders />} />
        <Route path="*" element={<Navigate to="/customer" replace />} />
      </Routes>
    </DashboardLayout>
  );
}

function courierLine(o: Order, restaurant: Restaurant | undefined, now: number) {
  if (o.status === 'picked_up') {
    const progress = deliveryProgress(o.pickedUpAt, now);
    const eta = restaurant && etaMinutes([restaurant.lat, restaurant.lng], [o.deliveryLat, o.deliveryLng], progress);
    return (
      <>
        <Icon name="bike" size={16} /> {o.courierName ?? 'Your courier'}{' '}
        {progress >= 1 ? 'is at your door' : `is on the way · arriving in about ${eta} min`}
      </>
    );
  }
  if (o.courierName && !TERMINAL.includes(o.status)) {
    return (
      <>
        <Icon name="bike" size={16} /> {o.courierName} will pick this up
      </>
    );
  }
  return (
    <>
      <Icon name="pin" size={16} /> {o.deliveryAddress}
    </>
  );
}

function MyOrders() {
  const user = useUser();
  const placedOrderId = (useLocation().state as { placedOrderId?: string } | null)?.placedOrderId;
  const { data, error, reload } = useLoad(() => Promise.all([getCustomerOrderStatuses(user.id), listRestaurants()]), [user.id]);
  const [actionError, setActionError] = useState('');
  const now = useNow(data?.[0].some((o) => o.status === 'picked_up') ? 1000 : null);

  async function cancel(order: Order) {
    setActionError('');
    try {
      await send(`Cancel order #${order.id}`, () => updateOrderStatus(order.id, 'cancelled'));
    } catch (e) {
      setActionError(errorMessage(e));
    }
    reload();
  }

  if (error) return <LoadError what="your orders" message={error} onRetry={reload} />;
  if (!data) return <Loading what="your orders" />;

  const [orders, restaurants] = data;
  const restaurantOf = (o: Order) => restaurants.find((r) => r.id === o.restaurantId);
  // Uber Eats-style live map once the bag is ready or a courier is involved.
  const showMap = (o: Order) => o.status === 'ready' || o.status === 'picked_up' || (!!o.courierName && !TERMINAL.includes(o.status));

  const newestFirst = [...orders].reverse();
  const active = newestFirst.filter((o) => !TERMINAL.includes(o.status));
  const past = newestFirst.filter((o) => TERMINAL.includes(o.status));

  const card = (o: Order, i: number) => (
    <OrderCard
      key={o.id}
      order={o}
      title={o.restaurantName}
      subtitle={courierLine(o, restaurantOf(o), now)}
      tracker
      media={showMap(o) && <OrderMap order={o} restaurant={restaurantOf(o)} />}
      style={stagger(i)}
    >
      {o.status === 'placed' && (
        <ConfirmButton label="Cancel order" question={`Cancel order #${o.id}?`} variant="danger" onConfirm={() => cancel(o)} />
      )}
    </OrderCard>
  );

  return (
    <>
      <div className="page-head">
        <div>
          <span className="eyebrow">Orders</span>
          <h1>My orders</h1>
          <p className="muted">You can cancel an order until the restaurant accepts it.</p>
        </div>
        <button className="btn btn-ghost btn-sm" onClick={reload}>
          ↻ Refresh
        </button>
      </div>

      {placedOrderId && (
        <p className="alert alert-success rise" role="status">
          <Icon name="check" /> <strong>Order #{placedOrderId} placed!</strong> The restaurant will confirm it shortly.
        </p>
      )}
      {actionError && (
        <p className="alert alert-error" role="alert">
          {actionError}
        </p>
      )}

      {orders.length === 0 ? (
        <Empty icon="bag" title="No orders yet">
          <Link to="/customer">Find something delicious to rescue →</Link>
        </Empty>
      ) : (
        <>
          <section className="stack">
            <h2 className="section-title">
              In progress <span className="count">{active.length}</span>
            </h2>
            {active.length === 0 ? <Empty icon="sparkle" title="Nothing in progress" /> : <div className="order-list">{active.map(card)}</div>}
          </section>
          {past.length > 0 && (
            <section className="stack">
              <h2 className="section-title">
                Past orders <span className="count">{past.length}</span>
              </h2>
              <div className="order-list">{past.map(card)}</div>
            </section>
          )}
        </>
      )}
    </>
  );
}

function OrderMap({ order, restaurant }: { order: Order; restaurant?: Restaurant }) {
  if (!restaurant) return null;
  return (
    <MapView
      height={290}
      deliveries={[
        {
          id: order.id,
          from: [restaurant.lat, restaurant.lng],
          fromLabel: restaurant.name,
          hue: restaurantHue(restaurant),
          to: [order.deliveryLat, order.deliveryLng],
          toLabel: 'You',
          courierName: order.courierName,
          pickedUpAt: order.status === 'picked_up' ? order.pickedUpAt : undefined,
        },
      ]}
    />
  );
}
