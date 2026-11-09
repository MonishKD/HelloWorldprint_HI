import { useEffect } from 'react';
import type { CourierDelivery, HistoryRow, LedgerRow } from '../../domain';
import { useUser } from '../../session';
import { Empty, LoadError, Loading, money, StatusBadge, timeOf, useLoad, usePolling } from '../common';
import { onCommandFinished } from '../dashboard/dashboardApi';
import { getHistory } from './prcApi';

const isLedger = (row: HistoryRow): row is LedgerRow => 'entryType' in row;

const ENTRY_LABEL = { charge: 'Charged', no_charge: 'No charge', refund: 'Refund' } as const;

/** The backend sends rows newest first, so a refund comes before its charge: put each refund right under its order's row. */
export function refundsUnderTheirOrder(rows: LedgerRow[]): LedgerRow[] {
  const firstSeen = new Map<string, number>();
  rows.forEach((r, i) => firstSeen.has(r.orderId) || firstSeen.set(r.orderId, i));
  return [...rows].sort((a, b) => firstSeen.get(a.orderId)! - firstSeen.get(b.orderId)! || Number(a.entryType === 'refund') - Number(b.entryType === 'refund'));
}

/**
 * Pricing & History (R-PRC-04, #265): the signed-in user's history, read from the backend (#264), which picks the rows
 * from the session. Couriers get their deliveries with no money; everyone else gets the ledger with each fare part.
 */
export default function HistoryTable({ open }: { open?: boolean }) {
  const user = useUser();
  const { data, error, reload } = useLoad(getHistory, [user.id]);
  useEffect(() => onCommandFinished(reload), [reload]);
  usePolling(reload);

  return (
    <details className="history" open={open}>
      <summary>
        Pricing &amp; history {data && <span className="count">{data.length}</span>}
      </summary>
      {error && !data ? (
        <LoadError what="your history" message={error} onRetry={reload} />
      ) : !data ? (
        <Loading what="your history" />
      ) : data.length === 0 ? (
        <Empty icon="inbox" title="Nothing in your history yet">
          Orders show up here once they are delivered, rejected or cancelled.
        </Empty>
      ) : user.role === 'courier' ? (
        <CourierRows rows={data.filter((r): r is CourierDelivery => !isLedger(r))} />
      ) : (
        <LedgerRows rows={refundsUnderTheirOrder(data.filter(isLedger))} showRestaurant={user.role !== 'restaurant'} />
      )}
    </details>
  );
}

function CourierRows({ rows }: { rows: CourierDelivery[] }) {
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th>Order</th>
            <th>Restaurant → Customer</th>
            <th>Delivered</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.orderId}>
              <td>
                <strong>#{r.orderId}</strong>
              </td>
              <td>
                {r.restaurantName} → {r.destinationName}
              </td>
              <td>{timeOf(r.deliveredAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function LedgerRows({ rows, showRestaurant }: { rows: LedgerRow[]; showRestaurant: boolean }) {
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th>Order</th>
            {showRestaurant && <th>Restaurant</th>}
            <th>Status</th>
            <th>Entry</th>
            <th className="num">Subtotal</th>
            <th className="num">Delivery</th>
            <th className="num">Service</th>
            <th className="num">Surcharge</th>
            <th className="num">Total</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={`${r.orderId}-${r.entryType}`} className={r.entryType === 'refund' ? 'is-refund' : undefined}>
              <td>
                <strong>#{r.orderId}</strong>
                <small className="muted block">{timeOf(r.finishedAt)}</small>
              </td>
              {showRestaurant && <td>{r.restaurantName}</td>}
              <td>
                <StatusBadge status={r.finalStatus} />
              </td>
              <td>{ENTRY_LABEL[r.entryType]}</td>
              <td className="num">{money(r.fare.subtotal)}</td>
              <td className="num">{money(r.fare.deliveryFee)}</td>
              <td className="num">{money(r.fare.serviceFee)}</td>
              <td className="num">{money(r.fare.surcharge)}</td>
              <td className="num">{money(r.fare.total)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
