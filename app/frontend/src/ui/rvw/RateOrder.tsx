import { useState, type KeyboardEvent } from 'react';
import type { Order, RatingScale } from '../../domain';
import { useLoad } from '../common';
import { errorMessage, send } from '../dashboard/dashboardApi';
import { Icon } from '../icons';
import { getRatingScale, rateOrder, ratingOf } from './rvwApi';

/** Words for a 1-5 scale; any other configured range just shows "n / max". */
const WORDS = ['Terrible', 'Bad', 'Okay', 'Good', 'Excellent'];
const wordFor = (n: number, scale: RatingScale) =>
  scale.minStars === 1 && scale.maxStars === 5 ? WORDS[n - 1] : `${n} / ${scale.maxStars}`;

/**
 * The rating section of a delivered order (#325, R-RVW-01). Once rated it shows what was given, and "Rate again" stays
 * usable on purpose: a second try reaches the backend, and its refusal shows here and in the console (demo 9.1).
 */
export function RateOrder({ order, restaurantName, onRated }: { order: Order; restaurantName: string; onRated: () => void }) {
  const [open, setOpen] = useState(false);
  const given = ratingOf(order);

  return (
    <section className="rate-order" aria-label={`Rating for order #${order.id}`}>
      {given ? (
        <div className="rate-done">
          <div className="rate-row">
            <span className="rate-who">Restaurant</span>
            <StaticStars n={given.restaurantStars} />
          </div>
          <div className="rate-row">
            <span className="rate-who">Courier</span>
            <StaticStars n={given.courierStars} />
          </div>
        </div>
      ) : (
        !open && (
          <div className="rate-prompt">
            <div>
              <strong>How was it?</strong>
              <small className="muted">Rate {restaurantName} and your courier.</small>
            </div>
          </div>
        )
      )}

      {open ? (
        <RateForm
          order={order}
          restaurantName={restaurantName}
          onClose={() => setOpen(false)}
          onRated={() => {
            setOpen(false);
            onRated();
          }}
        />
      ) : (
        <div className="rate-actions">
          <button type="button" className={`btn btn-sm ${given ? 'btn-ghost' : ''}`} onClick={() => setOpen(true)}>
            <Icon name="star" size={14} filled={!given} /> {given ? 'Rate again' : 'Rate this order'}
          </button>
        </div>
      )}
    </section>
  );
}

/** Read-only stars, e.g. on a rated order or the restaurant panel (rounded to the nearest whole star). */
export function StaticStars({ n, max = 5 }: { n: number; max?: number }) {
  const full = Math.round(n);
  return (
    <span className="stars-static" role="img" aria-label={`${n} out of ${max} stars`}>
      {Array.from({ length: max }, (_, i) => (
        <Icon key={i} name="star" size={16} filled={i < full} className={i < full ? '' : 'off'} />
      ))}
    </span>
  );
}

function RateForm({ order, restaurantName, onClose, onRated }: { order: Order; restaurantName: string; onClose: () => void; onRated: () => void }) {
  const { data: scale, error: scaleError } = useLoad(getRatingScale, []);
  const [restaurant, setRestaurant] = useState(0);
  const [courier, setCourier] = useState(0);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  if (scaleError) return <p className="alert alert-error" role="alert">Couldn't load the rating scale: {scaleError}</p>;
  if (!scale) return <p className="muted">Loading the rating scale…</p>;

  async function submit() {
    setError('');
    setBusy(true);
    try {
      await send(`Rate order #${order.id}`, () => rateOrder(order, restaurant, courier));
      onRated();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form
      className="rate-form rise"
      onSubmit={(e) => {
        e.preventDefault();
        submit();
      }}
    >
      <div className="rate-form-head">
        <strong>Rate order #{order.id}</strong>
        <small className="muted">Your stars help other rescuers pick where to order.</small>
      </div>

      <div className="rate-row">
        <div className="rate-who">
          <span>Restaurant</span>
          <small>{restaurantName}</small>
        </div>
        <StarPicker label={`Stars for ${restaurantName}`} scale={scale} value={restaurant} onChange={setRestaurant} />
      </div>

      <div className="rate-row">
        <div className="rate-who">
          <span>Courier</span>
          <small>{order.courierName ?? 'Your courier'}</small>
        </div>
        <StarPicker label={`Stars for ${order.courierName ?? 'the courier'}`} scale={scale} value={courier} onChange={setCourier} />
      </div>

      {error && (
        <p className="alert alert-error" role="alert">
          {error}
        </p>
      )}

      <div className="rate-actions">
        <button type="button" className="btn btn-ghost btn-sm" onClick={onClose}>
          Cancel
        </button>
        <button type="submit" className="btn btn-sm" disabled={busy || !restaurant || !courier}>
          {busy ? 'Sending…' : 'Send rating'}
        </button>
      </div>
    </form>
  );
}

/** Clickable stars as a radio group: hover previews, arrow keys move, one tab stop. */
function StarPicker({ label, scale, value, onChange }: { label: string; scale: RatingScale; value: number; onChange: (n: number) => void }) {
  const [hover, setHover] = useState(0);
  const stars = Array.from({ length: scale.maxStars - scale.minStars + 1 }, (_, i) => scale.minStars + i);
  const shown = hover || value;

  function onKey(e: KeyboardEvent) {
    const step = e.key === 'ArrowRight' || e.key === 'ArrowUp' ? 1 : e.key === 'ArrowLeft' || e.key === 'ArrowDown' ? -1 : 0;
    if (!step) return;
    e.preventDefault();
    const next = Math.min(scale.maxStars, Math.max(scale.minStars, (value || scale.minStars - step) + step));
    onChange(next);
    (e.currentTarget.querySelector(`[data-star="${next}"]`) as HTMLElement | null)?.focus();
  }

  return (
    <div className="star-field">
      <div className="star-picker" role="radiogroup" aria-label={label} onKeyDown={onKey} onMouseLeave={() => setHover(0)}>
        {stars.map((n) => (
          <button
            key={n}
            type="button"
            role="radio"
            data-star={n}
            aria-checked={n === value}
            aria-label={`${n} ${n === 1 ? 'star' : 'stars'} · ${wordFor(n, scale)}`}
            tabIndex={n === (value || scale.minStars) ? 0 : -1}
            className={`star-btn ${n <= shown ? 'is-on' : ''}`}
            onMouseEnter={() => setHover(n)}
            onClick={() => onChange(n)}
          >
            <Icon name="star" size={26} filled={n <= shown} />
          </button>
        ))}
      </div>
      <span className="star-word" aria-hidden="true">
        {shown ? wordFor(shown, scale) : 'Tap to rate'}
      </span>
    </div>
  );
}
