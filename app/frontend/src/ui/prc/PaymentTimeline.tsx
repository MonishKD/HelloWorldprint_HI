import { useUser } from '../../session';
import { money } from '../common';
import type { PaymentTrail } from './prcApi';

const STRIPE_TEST_PAYMENTS = 'https://dashboard.stripe.com/test/payments/';

const when = (iso: string) => new Date(iso).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });

/** "visa" + "4242" → "Visa •••• 4242", the way Stripe shows a card. */
export function cardLabel(brand?: string, last4?: string): string {
  if (!last4) return 'the card';
  const name = brand ? brand.charAt(0).toUpperCase() + brand.slice(1) : 'Card';
  return `${name} •••• ${last4}`;
}

/**
 * The order's payment from Stripe's side (#258, #266): held at checkout (PaymentIntent `requires_capture`), taken on
 * delivery (`succeeded`), and given back by the operator (a Stripe Refund). Customers and the operator see the same steps.
 */
export default function PaymentTimeline({ trail }: { trail: PaymentTrail }) {
  const user = useUser();
  const { summary, refund, paymentId } = trail;
  const card = cardLabel(summary.cardBrand, summary.cardLast4);
  const steps = [
    { label: `Held on ${card}`, stripe: 'requires_capture', at: summary.placedAt, amount: summary.fare.total },
    { label: 'Charged on delivery', stripe: 'succeeded', at: summary.deliveredAt, amount: summary.fare.total },
    ...(refund ? [{ label: `Refunded to ${card}`, stripe: 'refunded', at: refund.finishedAt, amount: refund.fare.total }] : []),
  ];

  return (
    <div className="stack">
      {refund && (
        <p className="alert alert-success" role="status">
          {user.role === 'customer'
            ? `${money(-refund.fare.total)} is on its way back to ${card}. Banks usually take 5 to 10 business days to show it.`
            : `Refund of ${money(-refund.fare.total)} sent through Stripe to the customer's ${card}.`}
        </p>
      )}
      <ol className="pay-trail" aria-label="Payment history in Stripe">
        {steps.map((s) => (
          <li key={s.stripe} className={s.stripe === 'refunded' ? 'is-refund' : undefined}>
            <span>
              {s.label}
              <small className="muted block">
                {s.at && when(s.at)} · Stripe: <code>{s.stripe}</code>
              </small>
            </span>
            <span className="num">{money(s.amount)}</span>
          </li>
        ))}
      </ol>
      {user.role === 'operator' && paymentId && (
        <a className="small" href={STRIPE_TEST_PAYMENTS + paymentId} target="_blank" rel="noreferrer">
          View payment <code>{paymentId}</code> in the Stripe dashboard ↗
        </a>
      )}
    </div>
  );
}
