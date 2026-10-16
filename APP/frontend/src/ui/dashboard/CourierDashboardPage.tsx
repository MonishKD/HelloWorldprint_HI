import { useState } from 'react';
import type { Order, Restaurant } from '../../domain';
import { useUser } from '../../session';
import { ConfirmButton, ConfirmChange, Empty, LoadError, Loading, minutesSince, money, restaurantHue, stagger, Stat, StatusBadge, Switch, TERMINAL, useLoad } from '../common';
import { Icon } from '../icons';
import MapView, { type MapDelivery, type MapPin } from '../map/MapView';
import { deliveryProgress, etaMinutes, point, useNow } from '../map/geo';
import { assignCourier, listCourierOrders, listDispatchQueue, listRestaurants, updateOrderStatus } from '../ord/ordApi';
import { listCouriers, setCourierOnline } from '../reg/regApi';
import { errorMessage, logError, send } from './dashboardApi';
import { DashboardLayout } from './DashboardRouter';

// Placeholder payout until the backend provides real delivery fees.
const payout = (o: Order) => 3.5 + o.total * 0.1;

export default function CourierDashboardPage() {
  return (
    <DashboardLayout>
      <CourierDashboard />
    </DashboardLayout>
  );
}

/** ETA text for a picked-up order; without coordinates (not geocoded yet) there is no estimate. */
function minutesLeft(r: Restaurant, o: Order, progress: number) {
  const from = point(r.lat, r.lng);
  const to = point(o.deliveryLat, o.deliveryLng);
  return from && to ? `About ${etaMinutes(from, to, progress)} min to drop-off` : 'On the way to drop-off';
}

function DeliveryRoute({ order, restaurant }: { order: Order; restaurant?: Restaurant }) {
  return (
    <ol className="route">
      <li>
        <span className="route-dot pickup-dot" aria-hidden="true" />
        <span>
          <small className="muted">Pick up</small>
          <strong>{order.restaurantName}</strong>
          {restaurant?.address && <small className="muted">{restaurant.address}</small>}
        </span>
      </li>
      <li>
        <span className="route-dot drop-dot" aria-hidden="true" />
        <span>
          <small className="muted">Drop off</small>
          <strong>{order.customerName}</strong>
          <small className="muted">{order.deliveryAddress}</small>
        </span>
      </li>
    </ol>
  );
}

function CourierDashboard() {
  const user = useUser();
  const { data, error, reload } = useLoad(
    () => Promise.all([listCouriers(), listCourierOrders(user.id), listDispatchQueue(), listRestaurants()]),
    [user.id],
  );
  const [actionError, setActionError] = useState('');
  const anyMoving = !!data?.[1].some((o) => o.status === 'picked_up');
  const now = useNow(anyMoving ? 1000 : null);

  async function run(action: string, call: () => Promise<unknown>) {
    setActionError('');
    try {
      await send(action, call);
    } catch (e) {
      setActionError(errorMessage(e));
    }
    reload();
  }

  if (error) return <LoadError what="your deliveries" message={error} onRetry={reload} />;
  if (!data) return <Loading what="your deliveries" />;

  const [couriers, mine, queue, restaurants] = data;
  const state = couriers.find((c) => c.id === user.id)?.courierState ?? 'offline';
  const online = state !== 'offline';
  const restaurantOf = (o: Order) => restaurants.find((r) => r.id === o.restaurantId);
  // One courier can carry several orders at once (Courier 1 — * Order in the domain model).
  const active = mine.filter((o) => !TERMINAL.includes(o.status));
  const delivered = mine.filter((o) => o.status === 'delivered').reverse();
  const earned = delivered.reduce((n, o) => n + payout(o), 0);

  const mapDeliveries: MapDelivery[] = active.flatMap((o) => {
    const r = restaurantOf(o);
    const from = point(r?.lat, r?.lng);
    const to = point(o.deliveryLat, o.deliveryLng);
    if (!r || !from || !to) return []; // no coordinates yet (not geocoded): the order is listed, just not drawn
    return [
      {
        id: o.id,
        from,
        fromLabel: r.name,
        hue: restaurantHue(r),
        to,
        toLabel: `#${o.id} · ${o.customerName}`,
        courierName: 'You',
        pickedUpAt: o.status === 'picked_up' ? o.pickedUpAt : undefined,
      },
    ];
  });
  const requestPins: MapPin[] = online
    ? [...new Set(queue.map((o) => o.restaurantId))].flatMap((id) => {
        const r = restaurants.find((x) => x.id === id);
        const count = queue.filter((o) => o.restaurantId === id).length;
        const at = point(r?.lat, r?.lng);
        return r && at ? [{ id: `req-${id}`, at, label: `${r.name} · ${count} request${count > 1 ? 's' : ''}`, hue: restaurantHue(r) }] : [];
      })
    : [];

  const title = !online ? "You're offline" : active.length ? `${active.length} active deliver${active.length > 1 ? 'ies' : 'y'}` : 'Online · looking for deliveries';

  return (
    <>
      <section className={`courier-hero rise ${online ? 'is-online' : ''}`}>
        <div>
          <span className="eyebrow">Courier</span>
          <h1>{title}</h1>
          <p>
            <StatusBadge status={state} />
          </p>
        </div>
        <div className="online-toggle">
          <span>{online ? 'Online' : 'Offline'}</span>
          <ConfirmChange question={online ? 'Go offline?' : 'Go online?'} onConfirm={() => run(online ? 'Go offline' : 'Go online', () => setCourierOnline(user.id, !online))}>
            {(ask) => (
              <Switch
                checked={online}
                label="Online"
                onChange={() => {
                  if (!active.length) return ask();
                  const message = 'Finish your active deliveries before going offline.';
                  logError(message); // every error shown is logged (R-DASH-06)
                  setActionError(message);
                }}
              />
            )}
          </ConfirmChange>
        </div>
      </section>

      {actionError && (
        <p className="alert alert-error" role="alert">
          {actionError}
        </p>
      )}

      <div className="stats">
        <Stat label="Earnings" value={money(earned)} hint="est. payouts" tone="brand" />
        <Stat label="Deliveries" value={delivered.length} />
        <Stat label="In your bag" value={active.length} hint={active.length ? `${active.filter((o) => o.status === 'picked_up').length} picked up` : 'Nothing yet'} />
        <Stat label="Requests nearby" value={online ? queue.length : '—'} tone={online && queue.length ? 'accent' : undefined} />
      </div>

      {(mapDeliveries.length > 0 || requestPins.length > 0) && (
        <section className="map-card rise">
          <MapView deliveries={mapDeliveries} pins={requestPins} height={320} />
          <div className="map-legend">
            <span>
              <i className="lg-route" /> Your routes
            </span>
            {requestPins.length > 0 && (
              <span>
                <i className="lg-request" /> Pickup requests
              </span>
            )}
          </div>
        </section>
      )}

      {active.length > 0 && (
        <section className="stack">
          <h2 className="section-title">
            Active deliveries <span className="count">{active.length}</span>
          </h2>
          <div className="job-grid">
            {active.map((o, i) => {
              const r = restaurantOf(o);
              const progress = deliveryProgress(o.pickedUpAt, now);
              return (
                <article key={o.id} className="job card rise" style={stagger(i)}>
                  <div className="job-head">
                    <div>
                      <span className="eyebrow">Delivery #{o.id}</span>
                      <StatusBadge status={o.status} />
                    </div>
                    <span className="payout">{money(payout(o))}</span>
                  </div>
                  <DeliveryRoute order={o} restaurant={r} />
                  {o.status === 'picked_up' && r && (
                    <p className="eta">
                      <Icon name="clock" size={16} />
                      {progress >= 1 ? 'You’ve arrived' : minutesLeft(r, o, progress)}
                    </p>
                  )}
                  <ul className="order-lines">
                    {o.lines.map((l) => (
                      <li key={l.itemId}>
                        <span className="qty-pill">{l.quantity}×</span> {l.name}
                      </li>
                    ))}
                  </ul>
                  {o.specialInstructions && <p className="note">“{o.specialInstructions}”</p>}
                  {o.status === 'picked_up' ? (
                    <ConfirmButton
                      block
                      variant="dark"
                      label="Mark as delivered"
                      question={`Delivered #${o.id} to ${o.customerName}?`}
                      onConfirm={() => run(`Order #${o.id}: picked_up → delivered`, () => updateOrderStatus(o.id, 'delivered'))}
                    />
                  ) : (
                    <>
                      {o.status !== 'ready' && <p className="muted small">The restaurant is still preparing this order.</p>}
                      <ConfirmButton
                        block
                        label="Confirm pickup"
                        question={`Picked up #${o.id}?`}
                        disabled={o.status !== 'ready'}
                        onConfirm={() => run(`Order #${o.id}: ready → picked_up`, () => updateOrderStatus(o.id, 'picked_up'))}
                      />
                    </>
                  )}
                </article>
              );
            })}
          </div>
        </section>
      )}

      <section className="stack">
        <h2 className="section-title">
          Delivery requests {online && <span className="count">{queue.length}</span>}
        </h2>
        {!online ? (
          <Empty icon="moon" title="You're offline">
            Go online to start receiving delivery requests.
          </Empty>
        ) : queue.length === 0 ? (
          <Empty icon="signal" title="No requests right now">
            New deliveries show up here as restaurants prepare them.
          </Empty>
        ) : (
          <div className="job-grid">
            {queue.map((o, i) => (
              <article key={o.id} className="job card rise" style={stagger(i)}>
                <div className="job-head">
                  <div>
                    <span className="eyebrow">
                      #{o.id} · {minutesSince(o.createdAt)}
                    </span>
                    <StatusBadge status={o.status === 'ready' ? 'ready' : 'preparing'} />
                  </div>
                  <span className="payout">{money(payout(o))}</span>
                </div>
                <DeliveryRoute order={o} restaurant={restaurantOf(o)} />
                <ConfirmButton
                  block
                  variant={active.length ? 'secondary' : 'primary'}
                  label={active.length ? 'Add to my route' : 'Accept delivery'}
                  question={`Take #${o.id} for ${money(payout(o))}?`}
                  onConfirm={() => run(`Accept delivery #${o.id}`, () => assignCourier(o.id, { id: user.id, name: user.name }))}
                />
              </article>
            ))}
          </div>
        )}
      </section>

      <details className="history">
        <summary>
          Delivery history <span className="count">{delivered.length}</span>
        </summary>
        {delivered.length === 0 ? (
          <Empty icon="bike" title="No deliveries yet" />
        ) : (
          <ul className="history-list">
            {delivered.map((o) => (
              <li key={o.id}>
                <span>
                  <strong>#{o.id}</strong> {o.restaurantName} → {o.customerName}
                </span>
                <span className="payout small">{money(payout(o))}</span>
              </li>
            ))}
          </ul>
        )}
      </details>
    </>
  );
}
