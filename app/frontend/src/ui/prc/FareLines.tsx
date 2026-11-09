import type { Fare } from '../../domain';
import { money } from '../common';

/**
 * The fare, one fee per line (R-PRC-01), so the total can be checked by hand.
 * Shared by the cart (#260), the order summary (#263) and the history (#265).
 */
export default function FareLines({ fare, totalLabel = 'Total' }: { fare: Fare; totalLabel?: string }) {
  return (
    <dl className="totals fare">
      <div>
        <dt>Subtotal</dt>
        <dd>{money(fare.subtotal)}</dd>
      </div>
      <div>
        <dt>Delivery fee</dt>
        <dd>{money(fare.deliveryFee)}</dd>
      </div>
      <div>
        <dt>Service fee</dt>
        <dd>{money(fare.serviceFee)}</dd>
      </div>
      <div>
        <dt>Small order surcharge</dt>
        <dd>{money(fare.surcharge)}</dd>
      </div>
      <div className="total">
        <dt>{totalLabel}</dt>
        <dd>{money(fare.total)}</dd>
      </div>
    </dl>
  );
}
