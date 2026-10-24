import { useState } from 'react';
import type { Order, OrderStatus } from '../../domain';
import { ConfirmButton, Empty, OrderCard, stagger } from '../common';
import { errorMessage, send } from '../dashboard/dashboardApi';
import { Icon, type IconName } from '../icons';
import { updateOrderStatus } from './ordApi';

// The happy path a restaurant moves an order along. Placed orders can also be rejected.
const NEXT_STEP: Partial<Record<OrderStatus, { status: OrderStatus; label: string }>> = {
  placed: { status: 'accepted', label: 'Accept' },
  accepted: { status: 'preparing', label: 'Start preparing' },
  preparing: { status: 'ready', label: 'Mark ready' },
};

function courierNote(o: Order) {
  const note = (icon: IconName, text: string) => (
    <>
      <Icon name={icon} size={16} /> {text}
    </>
  );
  if (o.status === 'ready') return o.courierName ? note('bike', `${o.courierName} is on the way to collect`) : note('hourglass', 'Waiting for a courier');
  if (o.status === 'picked_up') return note('bike', `Picked up by ${o.courierName}`);
  if (o.courierName && o.status === 'preparing') return note('bike', `${o.courierName} assigned`);
  return undefined;
}

export default function IncomingOrdersList({
  orders,
  emptyIcon,
  emptyText,
  positions,
  onChanged,
}: {
  orders: Order[];
  emptyIcon: IconName;
  emptyText: string;
  /** Preparation-queue position of each queued order (1 = next). Only the first may start preparing. */
  positions?: Record<string, number>;
  /** Refreshes the views behind this list; the buttons stay disabled until it has finished. */
  onChanged: () => Promise<unknown> | void;
}) {
  const [rejectingId, setRejectingId] = useState<string | null>(null);
  const [reason, setReason] = useState('');
  const [error, setError] = useState<{ id: string; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  async function changeStatus(order: Order, status: OrderStatus, rejectReason?: string) {
    setError(null);
    setBusy(true);
    const why = rejectReason?.trim();
    try {
      await send(`Order #${order.id}: ${order.status} → ${status}${why ? ` (reason: ${why})` : ''}`, () => updateOrderStatus(order.id, status, why));
      setRejectingId(null);
    } catch (e) {
      // The order stays where it is (nothing is removed before the backend agrees) and the reject form keeps what was typed.
      setError({ id: order.id, text: errorMessage(e) });
    }
    await onChanged();
    setBusy(false);
  }

  // The error sits on the order it belongs to; if that order has since moved on, it shows above the list instead.
  const onCard = !!error && orders.some((o) => o.id === error.id && NEXT_STEP[o.status]);
  const listError =
    error && !onCard ? (
      <p className="alert alert-error" role="alert">
        {error.text}
      </p>
    ) : null;

  if (orders.length === 0) {
    return (
      <>
        {listError}
        <Empty icon={emptyIcon} title={emptyText} />
      </>
    );
  }

  return (
    <div className="order-list">
      {listError}
      {orders.map((o, i) => {
        const next = NEXT_STEP[o.status];
        const position = positions?.[o.id];
        const queueNote = position && (
          <>
            <Icon name="hourglass" size={16} /> {position === 1 ? 'Next in the queue' : `Position ${position} in the queue`}
          </>
        );
        return (
          <OrderCard key={o.id} order={o} title={o.customerName} subtitle={courierNote(o) ?? queueNote} style={stagger(i)}>
            {next && (
              <>
                {o.status === 'placed' &&
                  (rejectingId === o.id ? (
                    <form
                      className="reject-form"
                      onSubmit={(e) => {
                        e.preventDefault();
                        changeStatus(o, 'rejected', reason);
                      }}
                    >
                      <label className="field">
                        <span>Why are you rejecting order #{o.id}?</span>
                        <textarea
                          required
                          autoFocus
                          placeholder="e.g. Item ran out before we could pack it"
                          value={reason}
                          onChange={(e) => setReason(e.target.value)}
                        />
                      </label>
                      <div className="order-actions">
                        <button type="button" className="btn btn-ghost btn-sm" disabled={busy} onClick={() => setRejectingId(null)}>
                          Back
                        </button>
                        <button className="btn btn-danger btn-sm" disabled={busy || !reason.trim()}>
                          Confirm reject
                        </button>
                      </div>
                    </form>
                  ) : (
                    <button
                      className="btn btn-danger"
                      disabled={busy}
                      onClick={() => {
                        setRejectingId(o.id);
                        setReason('');
                      }}
                    >
                      Reject
                    </button>
                  ))}
                {rejectingId !== o.id && (
                  <ConfirmButton
                    label={next.label}
                    question={`${next.label} order #${o.id}?`}
                    disabled={busy || (o.status === 'accepted' && position !== undefined && position > 1)}
                    onConfirm={() => changeStatus(o, next.status)}
                  />
                )}
                {onCard && error.id === o.id && (
                  <p className="alert alert-error" role="alert">
                    {error.text}
                  </p>
                )}
              </>
            )}
          </OrderCard>
        );
      })}
    </div>
  );
}
