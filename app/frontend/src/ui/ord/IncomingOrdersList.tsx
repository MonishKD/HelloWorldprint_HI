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
  onChanged: () => void;
}) {
  const [rejectingId, setRejectingId] = useState<string | null>(null);
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');

  async function changeStatus(order: Order, status: OrderStatus, rejectReason?: string) {
    setError('');
    try {
      const detail = rejectReason ? ` (reason: ${rejectReason.trim()})` : '';
      await send(`Order #${order.id}: ${order.status} → ${status}${detail}`, () => updateOrderStatus(order.id, status, rejectReason));
      setRejectingId(null);
    } catch (e) {
      setError(errorMessage(e));
    }
    onChanged();
  }

  if (orders.length === 0) return <Empty icon={emptyIcon} title={emptyText} />;

  return (
    <div className="order-list">
      {error && (
        <p className="alert alert-error" role="alert">
          {error}
        </p>
      )}
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
                        <button type="button" className="btn btn-ghost btn-sm" onClick={() => setRejectingId(null)}>
                          Back
                        </button>
                        <button className="btn btn-danger btn-sm" disabled={!reason.trim()}>
                          Confirm reject
                        </button>
                      </div>
                    </form>
                  ) : (
                    <button
                      className="btn btn-danger"
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
                    disabled={o.status === 'accepted' && position !== undefined && position > 1}
                    onConfirm={() => changeStatus(o, next.status)}
                  />
                )}
              </>
            )}
          </OrderCard>
        );
      })}
    </div>
  );
}
