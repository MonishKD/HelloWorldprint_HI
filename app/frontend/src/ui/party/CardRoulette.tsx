import { useEffect, useState, type CSSProperties } from 'react';
import type { PartyMember } from '../../domain';

/** A member's colour from their id; the golden angle keeps ids 1, 2, 3… far apart on the colour wheel. */
export const hueOf = (id: string) => Math.round(([...id].reduce((h, c) => (h * 31 + c.charCodeAt(0)) % 9973, 7) * 137.508) % 360);

const TURNS = 6; // full turns before the ring settles
const CARD_HALF_WIDTH = 110;
const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/**
 * Everyone's card on a 3D ring. With `payerId` and `spin`, the ring spins and slows down onto the payer the backend
 * picked (the animation never picks anyone itself); without `spin` it shows where it landed. `onDone` runs when it stops.
 */
export default function CardRoulette({ members, payerId, spin = false, onDone }: { members: PartyMember[]; payerId?: string; spin?: boolean; onDone?: () => void }) {
  const step = 360 / members.length;
  // Far enough out that neighbouring cards don't overlap.
  const radius = Math.round(CARD_HALF_WIDTH / Math.tan(Math.PI / Math.max(members.length, 3))) + 60;
  const payerIndex = members.findIndex((m) => m.id === payerId);
  const payer = members[payerIndex];
  const [angle, setAngle] = useState(payer && !spin ? -payerIndex * step : 0);
  const [landed, setLanded] = useState(!!payer && !spin);

  useEffect(() => {
    if (!spin || payerIndex < 0) return;
    if (reducedMotion()) {
      setAngle(-payerIndex * step);
      setLanded(true);
      onDone?.();
      return;
    }
    // After the start angle is painted, so the browser animates from it to the end one.
    const id = setTimeout(() => setAngle(-TURNS * 360 - payerIndex * step), 80);
    return () => clearTimeout(id);
  }, [spin, payerIndex]);

  const spinning = spin && !!payer && !landed;
  return (
    <div className={`roulette ${spinning ? 'is-spinning' : ''} ${landed ? 'is-landed' : ''} ${payer ? '' : 'is-idle'}`}>
      <div className="roulette-stage" aria-hidden="true">
        <span className="roulette-pointer" />
        <div
          className="roulette-ring"
          style={{ transform: `translateZ(-${radius}px) rotateY(${angle}deg)`, '--radius': `${radius}px` } as CSSProperties}
          onTransitionEnd={(e) => {
            if (e.target !== e.currentTarget || landed) return;
            setLanded(true);
            onDone?.();
          }}
        >
          {members.map((m, i) => (
            <div key={m.id} className="roulette-slot" style={{ transform: `rotateY(${i * step}deg) translateZ(${radius}px)` }}>
              <PartyCard member={m} picked={landed && m.id === payerId} dim={landed && m.id !== payerId} />
            </div>
          ))}
        </div>
      </div>
      <p className="roulette-verdict" aria-live="polite">
        {landed && payer ? (
          <>
            <strong>{payer.name}</strong> pays for everyone
          </>
        ) : spinning ? (
          'Spinning the cards…'
        ) : (
          `${members.length} cards on the table`
        )}
      </p>
    </div>
  );
}

/** A made-up card in the member's colour: just a name and decorative digits, never real card data. */
function PartyCard({ member, picked, dim }: { member: PartyMember; picked: boolean; dim: boolean }) {
  const hue = hueOf(member.id);
  return (
    <div className={`party-card ${picked ? 'is-picked' : ''} ${dim ? 'is-dim' : ''}`} style={{ '--hue': hue } as CSSProperties}>
      <span className="party-card-brand">Fortunate Pay</span>
      <span className="party-card-chip" />
      <span className="party-card-number">•••• •••• •••• {String((hue * 37) % 10000).padStart(4, '0')}</span>
      <span className="party-card-name">{member.name}</span>
    </div>
  );
}
