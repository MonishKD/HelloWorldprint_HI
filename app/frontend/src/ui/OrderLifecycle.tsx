import { useEffect, useState, type CSSProperties } from 'react';
import { Icon, type IconName } from './icons';

// Easter egg: the customer and courier are played by the team, recast every loop.
const CREW = ['Azmi', 'Monish', 'Darcy', 'Nameer', 'Kaue', 'Ahmed'];

interface Cast {
  customer: string;
  courier: string;
}

const randomName = () => CREW[Math.floor(Math.random() * CREW.length)];

/** Two different crew members, never the exact same pairing as last time. */
export function pickCast(previous?: Cast): Cast {
  for (;;) {
    const customer = randomName();
    const courier = randomName();
    if (customer === courier) continue;
    if (previous && previous.customer === customer && previous.courier === courier) continue;
    return { customer, courier };
  }
}

const STEPS: { icon: IconName; label: string; caption: (cast: Cast) => string }[] = [
  { icon: 'tag', label: 'Listed', caption: () => 'Green Leaf Bakery lists the croissants it didn’t sell this morning.' },
  { icon: 'bag', label: 'Reserved', caption: (c) => `${c.customer} grabs the box for $5.00 instead of $12.00.` },
  { icon: 'pot', label: 'Packed', caption: () => 'The kitchen packs it fresh and marks it ready.' },
  { icon: 'bike', label: 'On its way', caption: (c) => `${c.courier} picks it up and rides it across town.` },
  { icon: 'check', label: 'Rescued', caption: (c) => `Delivered to ${c.customer}. One less meal in the bin.` },
];

const STEP_MS = 2400;
const LAST = STEPS.length - 1;

/** Looping illustration of one order's journey; shows the finished state when motion is reduced. */
export default function OrderLifecycle() {
  const reduced = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
  const [step, setStep] = useState(reduced ? LAST : 0);
  const [cast, setCast] = useState(() => pickCast());

  useEffect(() => {
    if (reduced) return;
    const id = setTimeout(
      () => {
        if (step === LAST) setCast((previous) => pickCast(previous));
        setStep((step + 1) % STEPS.length);
      },
      step === LAST ? STEP_MS * 1.6 : STEP_MS,
    );
    return () => clearTimeout(id);
  }, [step, reduced]);

  return (
    <div className="lifecycle">
      <div className="lc-head">
        <span>Life of a rescued order</span>
        <span className="lc-receipt">
          Croissant box (4) <s>$12.00</s> <strong>$5.00</strong>
        </span>
      </div>
      <ol className="lc-track" style={{ '--progress': step / LAST } as CSSProperties}>
        {STEPS.map((s, i) => (
          <li key={s.label} className={i < step ? 'done' : i === step ? 'current' : ''}>
            <span className="lc-node">
              <Icon name={s.icon} size={18} />
            </span>
            <span className="lc-label">{s.label}</span>
          </li>
        ))}
      </ol>
      <p className="lc-caption" key={step}>
        {STEPS[step].caption(cast)}
      </p>
    </div>
  );
}
