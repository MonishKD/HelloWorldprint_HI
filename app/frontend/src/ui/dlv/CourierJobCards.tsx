import type { CourierJob } from '../../domain';
import { ConfirmButton, StatusBadge } from '../common';

/** Where to go and what to carry. No prices: a courier never sees payment details. */
function JobDetails({ job }: { job: CourierJob }) {
  return (
    <>
      <ol className="route">
        <li>
          <span className="route-dot pickup-dot" aria-hidden="true" />
          <span>
            <small className="muted">Pick up</small>
            <strong>{job.restaurantName}</strong>
            <small className="muted">{job.pickupAddress}</small>
          </span>
        </li>
        <li>
          <span className="route-dot drop-dot" aria-hidden="true" />
          <span>
            <small className="muted">Drop off</small>
            <strong>{job.destinationName}</strong>
            <small className="muted">{job.destinationAddress}</small>
          </span>
        </li>
      </ol>
      <ul className="order-lines">
        {job.lines.map((l) => (
          <li key={l.name}>
            <span className="qty-pill">{l.quantity}×</span> {l.name}
          </li>
        ))}
      </ul>
      {job.specialInstructions && <p className="note">“{job.specialInstructions}”</p>}
    </>
  );
}

function JobHead({ title, job }: { title: string; job: CourierJob }) {
  return (
    <div className="job-head">
      <div>
        <span className="eyebrow">
          {title} #{job.orderId}
        </span>
        <StatusBadge status={job.status} />
      </div>
      {job.donation && <StatusBadge status="donation" />}
    </div>
  );
}

export function OfferCard({ offer, onAccept, onDecline }: { offer: CourierJob; onAccept: () => void; onDecline: () => void }) {
  return (
    <div className="offer-card" aria-live="polite">
      <article className="job card rise">
        <JobHead title="New offer" job={offer} />
        <JobDetails job={offer} />
        <div className="job-actions">
          <ConfirmButton block label="Accept" question={`Accept order #${offer.orderId}?`} onConfirm={onAccept} />
          <ConfirmButton block variant="secondary" label="Decline" question={`Decline order #${offer.orderId}?`} onConfirm={onDecline} />
        </div>
      </article>
    </div>
  );
}

export function CurrentOrderCard({
  order,
  onDuty,
  onPickup,
  onDeliver,
}: {
  order: CourierJob;
  onDuty: boolean;
  onPickup: () => void;
  onDeliver: () => void;
}) {
  return (
    <article className="job card rise">
      <JobHead title="Delivery" job={order} />
      <JobDetails job={order} />
      {!onDuty && <p className="alert alert-error">You're off duty, so this order is stalled until an operator reassigns it.</p>}
      <div className="job-actions">
        <ConfirmButton
          block
          label="Confirm pickup"
          question={`Picked up order #${order.orderId}?`}
          disabled={!onDuty || order.status !== 'ready'} // R-DASH-05
          onConfirm={onPickup}
        />
        <ConfirmButton
          block
          variant="dark"
          label="Confirm delivery"
          question={`Delivered order #${order.orderId} to ${order.destinationName}?`}
          disabled={order.status !== 'picked_up'} // R-DASH-05
          onConfirm={onDeliver}
        />
      </div>
    </article>
  );
}
