import { useState } from 'react';
import type { CityRestaurant, Order, RestaurantStatus, StalledOrder, WaitingOrder } from '../../domain';
import { ConfirmButton, Empty, initials, LoadError, Loading, minutesSince, money, Monogram, restaurantHue, Segmented, Stat, StatusBadge, TERMINAL, useLoad, usePolling } from '../common';
import { Icon } from '../icons';
import { CityMapWithDetails } from '../map/RestaurantDetailsPanel';
import { setRestaurantStatus } from '../ord/ordApi';
import { errorMessage, getCityView, send } from './dashboardApi';
import { DashboardLayout } from './DashboardRouter';
import { cancelOrder, listAllOrders, listDispatchQueue, listStalledOrders, reassignOrder, refundOrder, resetSystem } from './operatorApi';

const FILTERS: Record<string, (o: Order) => boolean> = {
  All: () => true,
  Active: (o) => !TERMINAL.includes(o.status),
  Delivered: (o) => o.status === 'delivered',
  Issues: (o) => ['rejected', 'cancelled', 'refunded'].includes(o.status),
};

/** Six queued orders is the "long" band of R-DASH-03. */
const LONG_QUEUE = 6;
const BEFORE_PICKUP: Order['status'][] = ['placed', 'accepted', 'preparing', 'ready'];

export default function OperatorDashboardPage() {
  return (
    <DashboardLayout>
      <OperatorDashboard />
    </DashboardLayout>
  );
}

function alertsFor(restaurants: CityRestaurant[], queue: WaitingOrder[], stalled: StalledOrder[]) {
  const plural = (n: number) => (n > 1 ? 'orders' : 'order');
  return [
    ...(queue.length ? [`${queue.length} ${plural(queue.length)} waiting for a courier`] : []),
    ...(stalled.length ? [`${stalled.length} stalled ${plural(stalled.length)}: the courier went offline`] : []),
    ...restaurants.filter((r) => r.status === 'open' && r.queued >= LONG_QUEUE).map((r) => `${r.name} has ${r.queued} orders queued: consider pausing it`),
  ];
}

function DispatchQueuePanel({ queue }: { queue: WaitingOrder[] }) {
  return (
    <section className={queue.length ? 'attention' : 'card'}>
      <h2 className="section-title">
        Dispatch queue <span className="count">{queue.length}</span>
      </h2>
      {queue.length === 0 ? (
        <Empty icon="inbox" title="No orders waiting for a courier" />
      ) : (
        <ul>
          {queue.map((o) => (
            <li key={o.orderId}>
              <strong>#{o.orderId}</strong> {o.restaurantName}, ready {minutesSince(o.waitingSince)}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function StalledOrdersPanel({ stalled, onReassign }: { stalled: StalledOrder[]; onReassign: (o: StalledOrder) => void }) {
  return (
    <section className="card">
      <h2 className="section-title">
        Stalled orders <span className="count">{stalled.length}</span>
      </h2>
      {stalled.length === 0 ? (
        <Empty icon="bike" title="No stalled orders" />
      ) : (
        <ul className="roster">
          {stalled.map((o) => (
            <li key={o.orderId}>
              <span className="roster-name">
                <strong>
                  #{o.orderId} {o.restaurantName}
                </strong>
                <small className="muted">{o.courierName} went offline</small>
              </span>
              <ConfirmButton label="Reassign" question={`Reassign #${o.orderId}?`} onConfirm={() => onReassign(o)} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function OperatorDashboard() {
  const { data, error, reload } = useLoad(() => Promise.all([getCityView(), listAllOrders(), listDispatchQueue(), listStalledOrders()]), []);
  usePolling(reload);
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

  // Once loaded, a failed refresh keeps the page up: useLoad has already logged it to the console.
  if (!data) return error ? <LoadError what="the platform overview" message={error} onRetry={reload} /> : <Loading what="the platform overview" />;

  const [{ restaurants, couriers }, orders, queue, stalled] = data;
  const alerts = alertsFor(restaurants, queue, stalled);
  const shown = [...orders].reverse().filter(FILTERS[filter]);

  return (
    <>
      <div className="page-head rise">
        <div>
          <span className="eyebrow">Operator</span>
          <h1>Platform overview</h1>
          <p className="muted">Everything happening on FortunateGoods right now.</p>
        </div>
        <ConfirmButton variant="danger" label="Reset" question="Reset everything back to the seed?" onConfirm={() => run('Reset the system', resetSystem)} />
      </div>

      {actionError && (
        <p className="alert alert-error" role="alert">
          {actionError}
        </p>
      )}

      <div className="stats">
        <Stat label="Live orders" value={orders.filter(FILTERS.Active).length} tone="accent" />
        <Stat label="Waiting for a courier" value={queue.length} />
        <Stat label="Stalled" value={stalled.length} />
        <Stat label="Couriers on duty" value={couriers.length} />
        <Stat label="Restaurants open" value={`${restaurants.filter((r) => r.status === 'open').length}/${restaurants.length}`} />
      </div>

      {alerts.length > 0 && (
        <section className="attention rise">
          <h2 className="section-title">
            <Icon name="alert" /> Alerts <span className="count">{alerts.length}</span>
          </h2>
          <ul>
            {alerts.map((a) => (
              <li key={a}>{a}</li>
            ))}
          </ul>
        </section>
      )}

      <CityMapWithDetails
        restaurants={restaurants}
        couriers={couriers}
        homes={orders.filter(FILTERS.Active).flatMap((o) => (o.deliveryLat === undefined || o.deliveryLng === undefined ? [] : [{ id: o.id, lat: o.deliveryLat, lng: o.deliveryLng, label: `#${o.id} · ${o.customerName}` }]))}
        height={360}
      />

      <div className="split">
        <DispatchQueuePanel queue={queue} />
        <StalledOrdersPanel stalled={stalled} onReassign={(o) => run(`Reassign order #${o.orderId}`, () => reassignOrder(o.orderId))} />
      </div>

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
                      {o.createdAt && <small className="muted block">{minutesSince(o.createdAt)}</small>}
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
                      {BEFORE_PICKUP.includes(o.status) && (
                        <ConfirmButton variant="danger" label="Cancel" question={`Cancel #${o.id}?`} onConfirm={() => run(`Cancel order #${o.id}`, () => cancelOrder(o.id))} />
                      )}
                      {o.status === 'delivered' && (
                        <ConfirmButton
                          variant="secondary"
                          label="Refund"
                          question={`Refund ${money(o.total)} on #${o.id}?`}
                          onConfirm={() => run(`Refund order #${o.id}`, () => refundOrder(o.id))}
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
                    {r.status === 'closed' && 'Closed · '}
                    {r.queued} queued · {r.inPreparation} preparing · {r.ready} ready
                  </small>
                </span>
                <Segmented<RestaurantStatus>
                  label={`${r.name} status`}
                  value={r.status}
                  options={['open', 'paused']}
                  onChange={(s) => run(`Set ${r.name} to ${s}`, () => setRestaurantStatus(r.id, s))}
                  confirmText={(s) => `Set ${r.name} to ${s}?`}
                />
              </li>
            ))}
          </ul>
        </section>
        <section className="card">
          <h2>Couriers on duty</h2>
          {couriers.length === 0 ? (
            <Empty icon="moon" title="No couriers on duty" />
          ) : (
            <ul className="roster">
              {couriers.map((c) => (
                <li key={c.id}>
                  <span className="avatar" aria-hidden="true">
                    {initials(c.name)}
                  </span>
                  <span className="roster-name">
                    <strong>{c.name}</strong>
                  </span>
                  <StatusBadge status={c.courierState} />
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </>
  );
}
