import { useState } from 'react';
import type { CourierDashboard as Dashboard, CourierJob } from '../../domain';
import { ConfirmChange, Empty, LoadError, Loading, restaurantHue, StatusBadge, Switch, timeOf, useLoad, usePolling } from '../common';
import { CurrentOrderCard, OfferCard } from '../dlv/CourierJobCards';
import { acceptOffer, confirmDelivery, confirmPickup, declineOffer, getCourierDashboard, listDeliveries, setOnDuty } from '../dlv/dlvApi';
import MapView, { type MapDelivery } from '../map/MapView';
import { point } from '../map/geo';
import { errorMessage, logError, send } from './dashboardApi';
import { DashboardLayout } from './DashboardRouter';

export default function CourierDashboardPage() {
  return (
    <DashboardLayout>
      <CourierDashboard />
    </DashboardLayout>
  );
}

function heroTitle({ state, offer, order }: Dashboard) {
  if (state === 'offline') return "You're off duty";
  if (state === 'carrying' && order) return `Delivering to ${order.destinationName}`;
  if (order) return `Pick up at ${order.restaurantName}`;
  return offer ? 'New delivery offer' : "You're on duty";
}

function offDutyQuestion({ offer, order }: Dashboard) {
  if (order) return 'Go off duty? Your order will stall.';
  return offer ? 'Go off duty? This offer will be withdrawn.' : 'Go off duty?';
}

/** No coordinates (not geocoded yet): the job is still listed, just not drawn. */
function jobOnMap(job: CourierJob, courierName?: string): MapDelivery[] {
  const from = point(job.pickupLat, job.pickupLng);
  const to = point(job.destinationLat, job.destinationLng);
  if (!from || !to) return [];
  return [
    {
      id: job.orderId,
      from,
      fromLabel: job.restaurantName,
      hue: restaurantHue({ id: job.restaurantName }),
      to,
      toLabel: `#${job.orderId} · ${job.destinationName}`,
      courierName,
      pickedUpAt: job.pickedUpAt,
    },
  ];
}

function CourierDashboard() {
  const { data, error, reload } = useLoad(() => Promise.all([getCourierDashboard(), listDeliveries()]), []);
  usePolling(reload); // a new offer shows up without reloading the page
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
  if (!data) return error ? <LoadError what="your deliveries" message={error} onRetry={reload} /> : <Loading what="your deliveries" />;

  const [dashboard, deliveries] = data;
  const { state, offer, order } = dashboard;
  const onDuty = state !== 'offline';
  const mapDeliveries = order ? jobOnMap(order, 'You') : offer ? jobOnMap(offer) : [];

  return (
    <>
      <section className={`courier-hero rise ${onDuty ? 'is-online' : ''}`}>
        <div>
          <span className="eyebrow">Courier</span>
          <h1>{heroTitle(dashboard)}</h1>
          <p>
            <StatusBadge status={state} />
          </p>
        </div>
        <div className="online-toggle">
          <span>{onDuty ? 'On duty' : 'Off duty'}</span>
          <ConfirmChange
            question={onDuty ? offDutyQuestion(dashboard) : 'Go on duty?'}
            onConfirm={() => run(onDuty ? 'Go off duty' : 'Go on duty', () => setOnDuty(!onDuty))}
          >
            {(ask) => (
              <Switch
                checked={onDuty}
                label="On duty"
                onChange={() => {
                  if (state !== 'carrying') return ask();
                  const message = 'Deliver your order before going off duty.'; // R-DLV-03
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

      {order && (
        <CurrentOrderCard
          key={order.orderId}
          order={order}
          onDuty={onDuty}
          onPickup={() => run(`Order #${order.orderId}: ready → picked_up`, confirmPickup)}
          onDeliver={() => run(`Order #${order.orderId}: picked_up → delivered`, confirmDelivery)}
        />
      )}

      {offer && (
        <OfferCard
          key={offer.orderId}
          offer={offer}
          onAccept={() => run(`Accept order #${offer.orderId}`, acceptOffer)}
          onDecline={() => run(`Decline order #${offer.orderId}`, declineOffer)}
        />
      )}

      {!order && !offer && (onDuty ? (
        <Empty icon="signal" title="No requests right now">
          You'll be offered an order when a restaurant marks one ready.
        </Empty>
      ) : (
        <Empty icon="moon" title="You're off duty">
          Go on duty to start receiving delivery offers.
        </Empty>
      ))}

      {mapDeliveries.length > 0 && (
        <section className="map-card rise">
          <MapView deliveries={mapDeliveries} height={300} />
        </section>
      )}

      <details className="history">
        <summary>
          Delivery history <span className="count">{deliveries.length}</span>
        </summary>
        {deliveries.length === 0 ? (
          <Empty icon="bike" title="No deliveries yet" />
        ) : (
          <ul className="history-list">
            {[...deliveries].reverse().map((d) => (
              <li key={d.orderId}>
                <span>
                  <strong>#{d.orderId}</strong> {d.restaurantName} → {d.destinationName}
                </span>
                <span className="muted small">{timeOf(d.deliveredAt)}</span>
              </li>
            ))}
          </ul>
        )}
      </details>
    </>
  );
}
