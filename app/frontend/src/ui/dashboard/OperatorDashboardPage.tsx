import { useState } from 'react';
import type { Order, OrderStatus, RestaurantStatus } from '../../domain';
import { ConfirmButton, Empty, initials, LoadError, Loading, minutesSince, money, Monogram, restaurantHue, Segmented, Stat, StatusBadge, TERMINAL, useLoad } from '../common';
import { Icon } from '../icons';
import CityMap from '../map/CityMap';
import { listAllOrders, listCourierLocations, listRestaurants, setRestaurantStatus, updateOrderStatus } from '../ord/ordApi';
import { listCouriers } from '../reg/regApi';
import { errorMessage, send } from './dashboardApi';
import { DashboardLayout } from './DashboardRouter';

const FILTERS: Record<string, (o: Order) => boolean> = {
  All: () => true,
  Active: (o) => !TERMINAL.includes(o.status),
  Delivered: (o) => o.status === 'delivered',
  Issues: (o) => ['rejected', 'cancelled', 'refunded'].includes(o.status),
};

const NOT_EARNING: OrderStatus[] = ['rejected', 'cancelled', 'refunded'];
const STALE_MINUTES = 10;

export default function OperatorDashboardPage() {
  return (
    <DashboardLayout>
      <OperatorDashboard />
    </DashboardLayout>
  );
}

function OperatorDashboard() {
  const { data, error, reload } = useLoad(() => Promise.all([listAllOrders(), listRestaurants(), listCouriers(), listCourierLocations()]), []);
  const [filter, setFilter] = useState('Active');
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

  if (error) return <LoadError what="the platform overview" message={error} onRetry={reload} />;
  if (!data) return <Loading what="the platform overview" />;

  const [orders, restaurants, couriers, courierLocations] = data;
  const newestFirst = [...orders].reverse();
  const earning = orders.filter((o) => !NOT_EARNING.includes(o.status));
  const meals = earning.flatMap((o) => o.lines).reduce((n, l) => n + l.quantity, 0);
  const online = couriers.filter((c) => c.courierState !== 'offline').length;
  const ageMin = (o: Order) => (Date.now() - new Date(o.createdAt).getTime()) / 60_000;
  const attention = orders.filter(
    (o) => (o.status === 'placed' && ageMin(o) > STALE_MINUTES) || (o.status === 'ready' && !o.courierId),
  );
  const shown = newestFirst.filter(FILTERS[filter]);

  return (
    <>
      <div className="page-head rise">
        <div>
          <span className="eyebrow">Operator</span>
          <h1>Platform overview</h1>
          <p className="muted">Everything happening on FortunateGoods right now.</p>
        </div>
        <button className="btn btn-ghost btn-sm" onClick={reload}>
          ↻ Refresh
        </button>
      </div>

      {actionError && (
        <p className="alert alert-error" role="alert">
          {actionError}
        </p>
      )}

      <div className="stats">
        <Stat label="Live orders" value={orders.filter(FILTERS.Active).length} tone="accent" />
        <Stat label="Order volume" value={money(earning.reduce((n, o) => n + o.total, 0))} hint={`${orders.length} orders total`} tone="brand" />
        <Stat label="Meals rescued" value={meals} hint={`≈ ${(meals * 2.5).toFixed(1)} kg CO₂e`} />
        <Stat label="Couriers online" value={`${online}/${couriers.length}`} />
        <Stat label="Restaurants open" value={`${restaurants.filter((r) => r.status === 'open').length}/${restaurants.length}`} />
      </div>

      <CityMap
        restaurants={restaurants}
        couriers={courierLocations}
        homes={orders
          .filter((o) => !TERMINAL.includes(o.status))
          .flatMap((o) => (o.deliveryLat === undefined || o.deliveryLng === undefined ? [] : [{ id: o.id, lat: o.deliveryLat, lng: o.deliveryLng, label: `#${o.id} · ${o.customerName}` }]))}
        height={360}
      />

      {attention.length > 0 && (
        <section className="attention rise">
          <h2 className="section-title">
            <Icon name="alert" /> Needs attention <span className="count">{attention.length}</span>
          </h2>
          <ul>
            {attention.map((o) => (
              <li key={o.id}>
                <strong>#{o.id}</strong> {o.restaurantName}:{' '}
                {o.status === 'placed' ? `not accepted after ${minutesSince(o.createdAt).replace(' ago', '')}` : 'ready, but no courier assigned'}
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="card">
        <div className="page-head">
          <h2>Orders</h2>
          <div className="chips" role="group" aria-label="Filter orders">
            {Object.keys(FILTERS).map((f) => (
              <button key={f} type="button" className="chip" aria-pressed={filter === f} onClick={() => setFilter(f)}>
                {f} <span className="chip-count">{orders.filter(FILTERS[f]).length}</span>
              </button>
            ))}
          </div>
        </div>
        {shown.length === 0 ? (
          <Empty icon="inbox" title={`No ${filter.toLowerCase()} orders`} />
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Order</th>
                  <th>Restaurant → Customer</th>
                  <th>Courier</th>
                  <th>Status</th>
                  <th className="num">Total</th>
                  <th aria-label="Actions" />
                </tr>
              </thead>
              <tbody>
                {shown.map((o) => (
                  <tr key={o.id}>
                    <td>
                      <strong>#{o.id}</strong>
                      <small className="muted block">{minutesSince(o.createdAt)}</small>
                    </td>
                    <td>
                      {o.restaurantName} → {o.customerName}
                    </td>
                    <td>{o.courierName ?? <span className="muted">—</span>}</td>
                    <td>
                      <StatusBadge status={o.status} />
                    </td>
                    <td className="num">{money(o.total)}</td>
                    <td className="row-actions">
                      {!TERMINAL.includes(o.status) && o.status !== 'picked_up' && (
                        <ConfirmButton
                          variant="danger"
                          label="Cancel"
                          question={`Cancel #${o.id}?`}
                          onConfirm={() => run(`Operator cancel order #${o.id}`, () => updateOrderStatus(o.id, 'cancelled'))}
                        />
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <div className="split">
        <section className="card">
          <h2>Restaurants</h2>
          <ul className="roster">
            {restaurants.map((r) => (
              <li key={r.id}>
                <Monogram name={r.name} hue={restaurantHue(r)} />
                <span className="roster-name">
                  <strong>{r.name}</strong>
                  <small className="muted">
                    {r.availableCount}/{r.itemCount} items available
                  </small>
                </span>
                <Segmented<RestaurantStatus>
                  label={`${r.name} status`}
                  value={r.status}
                  options={['open', 'paused', 'closed']}
                  onChange={(s) => run(`Set ${r.name} to ${s}`, () => setRestaurantStatus(r.id, s))}
                  confirmText={(s) => `Set ${r.name} to ${s}?`}
                />
              </li>
            ))}
          </ul>
        </section>
        <section className="card">
          <h2>Couriers</h2>
          <ul className="roster">
            {couriers.map((c) => (
              <li key={c.id}>
                <span className="avatar" aria-hidden="true">
                  {initials(c.name)}
                </span>
                <span className="roster-name">
                  <strong>{c.name}</strong>
                  <small className="muted">{orders.filter((o) => o.courierId === c.id && o.status === 'delivered').length} delivered</small>
                </span>
                <StatusBadge status={c.courierState ?? 'offline'} />
              </li>
            ))}
          </ul>
        </section>
      </div>
    </>
  );
}
