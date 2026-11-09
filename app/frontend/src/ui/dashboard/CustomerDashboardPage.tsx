import { useState } from 'react';
import { Link, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import type { Order, Restaurant } from '../../domain';
import { useUser } from '../../session';
import { ConfirmButton, Empty, LoadError, Loading, OrderCard, restaurantHue, stagger, StatusBadge, TERMINAL, timeOf, useLoad, usePolling } from '../common';
import MenuPage from '../ord/MenuPage';
import CityMapPanel from '../map/CityMapPanel';
import MapView from '../map/MapView';
import { deliveryProgress, etaMinutes, point, useNow } from '../map/geo';
import { cancelOrder, listRestaurants } from '../ord/ordApi';
import RestaurantListPage from '../ord/RestaurantListPage';
import HistoryTable from '../prc/HistoryTable';
import OrderSummaryPanel from '../prc/OrderSummaryPanel';
import { errorMessage, getDashboardOrders, send } from './dashboardApi';
import { Icon } from '../icons';
import { DashboardLayout } from './DashboardRouter';

/** A summary exists once the order was charged, i.e. delivered (a refund keeps it). */
const HAS_SUMMARY: Order['status'][] = ['delivered', 'refunded'];

export default function CustomerDashboardPage() {
  return (
    <DashboardLayout>
      <Routes>
        <Route index element={<RestaurantListPage />} />
        <Route path="r/:restaurantId" element={<MenuPage />} />
        <Route path="map" element={<CustomerCityMap />} />
        <Route path="orders" element={<MyOrders />} />
        <Route path="history" element={<HistoryTable open />} />
        <Route path="*" element={<Navigate to="/customer" replace />} />
      </Routes>
    </DashboardLayout>
  );
}

function CustomerCityMap() {
  return <CityMapPanel />;
}

function courierLine(o: Order, restaurant: Restaurant | undefined, now: number) {
  if (o.status === 'picked_up') {
    const progress = deliveryProgress(o.pickedUpAt, now);
    const from = point(restaurant?.lat, restaurant?.lng);
    const to = point(o.deliveryLat, o.deliveryLng);
    const eta = from && to ? etaMinutes(from, to, progress) : undefined;
    return (
      <>
        <Icon name="bike" size={16} /> {o.courierName ?? 'Your courier'}{' '}
        {progress >= 1 ? 'is at your door' : eta === undefined ? 'is on the way' : `is on the way · arriving in about ${eta} min`}
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

function handoffTimes(o: Order) {
  return [
    o.pickedUpAt && `Picked up ${timeOf(o.pickedUpAt)}${o.courierName ? ` by ${o.courierName}` : ''}`,
    o.deliveredAt && `Delivered ${timeOf(o.deliveredAt)}`,
  ]
    .filter(Boolean)
    .join(' · ');
}

function MyOrders() {
  const user = useUser();
  const placed = (useLocation().state as { placed?: { id: string; status: string } } | null)?.placed;
  const { data, error, reload } = useLoad(() => Promise.all([getDashboardOrders(), listRestaurants()]), [user.id]);
  usePolling(reload);
  const [actionError, setActionError] = useState('');
  const [cancelled, setCancelled] = useState('');
  const now = useNow(data?.[0].some((o) => o.status === 'picked_up') ? 1000 : null);

  async function cancel(order: Order) {
    setActionError('');
    setCancelled('');
    try {
      await send(`Cancel order #${order.id}`, () => cancelOrder(order.id));
      setCancelled(order.id);
    } catch (e) {
      setActionError(errorMessage(e));
    }
    reload();
  }

  if (error && !data) return <LoadError what="your orders" message={error} onRetry={reload} />;
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
      title={restaurantOf(o)?.name ?? o.restaurantName}
      subtitle={courierLine(o, restaurantOf(o), now)}
      tracker
      media={HAS_SUMMARY.includes(o.status) ? <OrderSummaryPanel orderId={o.id} /> : showMap(o) && <OrderMap order={o} restaurant={restaurantOf(o)} />}
      style={stagger(i)}
    >
      {handoffTimes(o) && <small className="muted">{handoffTimes(o)}</small>}
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

      {placed && placed.id !== cancelled && (
        <p className="alert alert-success rise" role="status">
          <Icon name="check" /> <strong>Order #{placed.id} placed!</strong> <StatusBadge status={placed.status} /> The restaurant will confirm it shortly.
        </p>
      )}
      {cancelled && (
        <p className="alert alert-success rise" role="status">
          <Icon name="check" /> <strong>Order #{cancelled} cancelled.</strong>
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
  const from = point(restaurant?.lat, restaurant?.lng);
  const to = point(order.deliveryLat, order.deliveryLng);
  if (!restaurant || !from || !to) return null; // no coordinates yet (not geocoded): no map for this order
  return (
    <MapView
      height={290}
      deliveries={[
        {
          id: order.id,
          from,
          fromLabel: restaurant.name,
          hue: restaurantHue(restaurant),
          to,
          toLabel: 'You',
          courierName: order.courierName,
          pickedUpAt: order.status === 'picked_up' ? order.pickedUpAt : undefined,
        },
      ]}
    />
  );
}
