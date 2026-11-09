import { useState } from 'react';
import type { OrderSummary } from '../../domain';
import { money, timeOf } from '../common';
import { errorMessage } from '../dashboard/dashboardApi';
import FareLines from './FareLines';
import { getOrderSummary } from './prcApi';

/**
 * A delivered order's summary (R-PRC-02, #263): read from the backend when opened, so the fee lines and the total
 * are exactly what was charged (#262), not recomputed here.
 */
export default function OrderSummaryPanel({ orderId }: { orderId: string }) {
  const [summary, setSummary] = useState<OrderSummary | null>(null);
  const [error, setError] = useState('');

  function load(open: boolean) {
    if (!open || summary) return;
    setError('');
    getOrderSummary(orderId).then(setSummary, (e) => setError(errorMessage(e)));
  }

  return (
    <details className="summary-panel" onToggle={(e) => load(e.currentTarget.open)}>
      <summary>Order summary</summary>
      {error ? (
        <p className="alert alert-error" role="alert">
          {error}
        </p>
      ) : !summary ? (
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
            {summary.cardLast4 && ` · ${summary.cardBrand ?? 'Card'} ending in ${summary.cardLast4}`}
          </p>
        </div>
      )}
    </details>
  );
}
