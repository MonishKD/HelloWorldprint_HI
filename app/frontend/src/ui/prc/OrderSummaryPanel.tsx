import { useEffect, useState } from 'react';
import { money, timeOf } from '../common';
import { errorMessage } from '../dashboard/dashboardApi';
import FareLines from './FareLines';
import PaymentTimeline from './PaymentTimeline';
import { getPaymentTrail, type PaymentTrail } from './prcApi';

/**
 * A delivered order's summary (R-PRC-02, #263): read from the backend when opened, so the fee lines and the total
 * are exactly what was charged (#262), not recomputed here. Below it, the payment as Stripe saw it, refund included.
 * `refunded` reloads it when the order is refunded while it is open (the operator's view).
 */
export default function OrderSummaryPanel({ orderId, refunded = false, open = false }: { orderId: string; refunded?: boolean; open?: boolean }) {
  const [opened, setOpened] = useState(open);
  const [trail, setTrail] = useState<PaymentTrail | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!opened) return;
    setError('');
    getPaymentTrail(orderId).then(setTrail, (e) => setError(errorMessage(e)));
  }, [opened, orderId, refunded]);

  const summary = trail?.summary;
  return (
    <details className="summary-panel" open={open} onToggle={(e) => e.currentTarget.open && setOpened(true)}>
      <summary>{refunded ? 'Order summary · refunded' : 'Order summary'}</summary>
      {error ? (
        <p className="alert alert-error" role="alert">
          {error}
        </p>
      ) : !trail || !summary ? (
        <p className="muted small">Loading the summary…</p>
      ) : (
        <div className="stack">
          <ul className="summary-lines">
            {summary.lines.map((l, i) => (
              <li key={i}>
                <span>
                  <span className="qty-pill">{l.quantity}×</span> {l.name}
                </span>
                <span>{money(l.price * l.quantity)}</span>
              </li>
            ))}
          </ul>
          <FareLines fare={summary.fare} totalLabel="Total charged" />
          <p className="muted small">
            Placed {timeOf(summary.placedAt)}, delivered {timeOf(summary.deliveredAt)}
            {summary.courierName && ` by ${summary.courierName}`} to {summary.deliveryAddress}
          </p>
          <PaymentTimeline trail={trail} />
        </div>
      )}
    </details>
  );
}
