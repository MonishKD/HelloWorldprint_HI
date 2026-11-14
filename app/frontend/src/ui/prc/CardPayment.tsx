import { Elements, PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js';
import { loadStripe, type Stripe } from '@stripe/stripe-js';
import { ConfirmButton } from '../common';

// Stripe test mode (#222). The publishable key reaches the build through docker-compose.yml (VITE_STRIPE_PUBLISHABLE_KEY).
const KEY: string | undefined = import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY;
let stripe: Promise<Stripe | null> | null = null;
/** Stripe.js is only fetched once a customer reaches the card step. */
const getStripe = () => (stripe ??= KEY ? loadStripe(KEY) : Promise.resolve(null));

/** Confirms the card against the checkout's client secret; throws Stripe's own message when it is declined. */
export type ConfirmCard = () => Promise<void>;

/**
 * Stripe's Payment Element for the amount held at checkout (#260). The backend restricts the payment to cards,
 * so only the card form shows. `onPay` gets a function that confirms the card, and decides what happens around it.
 */
export default function CardPayment({
  clientSecret,
  label,
  question,
  disabled,
  onPay,
}: {
  clientSecret: string;
  label: string;
  question: string;
  disabled?: boolean;
  onPay: (confirmCard: ConfirmCard) => void;
}) {
  if (!KEY) return <p className="alert alert-warn">Card payments need VITE_STRIPE_PUBLISHABLE_KEY in .env.</p>;
  const theme = document.documentElement.dataset.theme === 'dark' ? 'night' : 'stripe';
  return (
    // Keyed by the secret: Elements can't switch to another payment once mounted.
    <Elements key={clientSecret} stripe={getStripe()} options={{ clientSecret, appearance: { theme } }}>
      <CardForm label={label} question={question} disabled={disabled} onPay={onPay} />
    </Elements>
  );
}

function CardForm({ label, question, disabled, onPay }: { label: string; question: string; disabled?: boolean; onPay: (confirmCard: ConfirmCard) => void }) {
  const stripe = useStripe();
  const elements = useElements();

  async function confirmCard() {
    if (!stripe || !elements) throw new Error('The card form is still loading.');
    // redirect 'if_required': a card never redirects, so the page stays and the order is placed right after.
    const { error } = await stripe.confirmPayment({ elements, redirect: 'if_required' });
    if (error) throw new Error(error.message ?? 'The card was declined.');
  }

  return (
    <div className="card-payment">
      <PaymentElement />
      <ConfirmButton block variant="dark" label={label} question={question} disabled={disabled || !stripe || !elements} onConfirm={() => onPay(confirmCard)} />
    </div>
  );
}
