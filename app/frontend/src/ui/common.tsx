import { useCallback, useEffect, useState, type CSSProperties, type DependencyList, type ReactNode } from 'react';
import type { Order, OrderStatus } from '../domain';
import { errorMessage, logError } from './dashboard/dashboardApi';
import { Icon, type IconName } from './icons';

export const money = (n: number) => n.toLocaleString('en-CA', { style: 'currency', currency: 'CAD' });

export const timeOf = (iso: string) => new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

export function minutesSince(iso: string): string {
  const mins = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60_000));
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins} min ago`;
  return `${Math.floor(mins / 60)} h ${mins % 60} min ago`;
}

/** Staggered entrance animation index for list items (see .rise in styles.css). */
export const stagger = (i: number) => ({ '--i': Math.min(i, 12) }) as CSSProperties;

export const initials = (name: string) =>
  name
    .split(/\s+/)
    .slice(0, 2)
    .map((w) => w[0]?.toUpperCase())
    .join('');

/** Cosmetic accent colour for a restaurant. The domain model has no colour, so it's derived from the id unless one is given. */
export const restaurantHue = (r: { id: string; hue?: number }) => r.hue ?? [...r.id].reduce((h, c) => (h * 31 + c.charCodeAt(0)) % 360, 7);

export const TERMINAL: OrderStatus[] = ['delivered', 'rejected', 'cancelled', 'refunded'];

export function Logo() {
  return (
    <span className="logo">
      <span className="logo-mark" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="16" height="16">
          <path
            fill="currentColor"
            d="M20 3c-9 0-15 4-15 11 0 2 .6 3.6 1.6 4.9L4 21.5 5.5 23l2.6-2.6C9.4 21.4 11 22 13 22c7 0 9-8 7-19Zm-7 17c-1.3 0-2.5-.3-3.4-.9 2.4-3.7 5-6.2 8.4-8.1-3.9 1.3-7 3.8-9.8 7.6-.5-.9-.7-1.9-.7-3.1 0-5.6 4.6-8.8 11.6-9.4.8 7.6-.9 13.9-6.1 13.9Z"
          />
        </svg>
      </span>
      <span className="wordmark">
        Fortunate<b>Goods</b>
      </span>
    </span>
  );
}

// ---------- Theme ----------

type Theme = 'light' | 'dark';

function readTheme(): Theme {
  try {
    const saved = localStorage.getItem('fg-theme');
    if (saved === 'light' || saved === 'dark') return saved;
  } catch {
    // storage blocked: fall back to the system preference
  }
  return matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>(readTheme);
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try {
      localStorage.setItem('fg-theme', theme);
    } catch {
      // not critical
    }
  }, [theme]);
  const next = theme === 'dark' ? 'light' : 'dark';
  return (
    <button type="button" className="icon-btn" aria-label={`Switch to ${next} mode`} title={`Switch to ${next} mode`} onClick={() => setTheme(next)}>
      {theme === 'dark' ? (
        <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
          <circle cx="12" cy="12" r="4.5" fill="currentColor" />
          <path stroke="currentColor" strokeWidth="2" strokeLinecap="round" d="M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
        </svg>
      ) : (
        <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
          <path fill="currentColor" d="M20.5 14.6A8.5 8.5 0 0 1 9.4 3.5 8.5 8.5 0 1 0 20.5 14.6Z" />
        </svg>
      )}
    </button>
  );
}

// ---------- Small building blocks ----------

export function StatusBadge({ status }: { status: string }) {
  return <span className={`badge badge-${status}`}>{status.replace('_', ' ')}</span>;
}

export function Stat({ label, value, hint, tone }: { label: string; value: ReactNode; hint?: string; tone?: 'brand' | 'accent' }) {
  return (
    <div className={`stat ${tone ? `stat-${tone}` : ''}`}>
      <span className="stat-label">{label}</span>
      <strong className="stat-value">{value}</strong>
      {hint && <span className="stat-hint">{hint}</span>}
    </div>
  );
}

export function Switch({ checked, onChange, label }: { checked: boolean; onChange: () => void; label: string }) {
  return (
    <button type="button" role="switch" aria-checked={checked} aria-label={label} className="switch" onClick={onChange}>
      <span className="switch-thumb" />
    </button>
  );
}

export function Segmented<T extends string>({
  value,
  options,
  onChange,
  label,
  confirmText,
}: {
  value: T;
  options: T[];
  onChange: (value: T) => void;
  label: string;
  /** If given, picking an option first asks this question (nothing is sent until the user confirms). */
  confirmText?: (value: T) => string;
}) {
  const [pending, setPending] = useState<T | null>(null);
  if (pending !== null && confirmText) {
    return (
      <ConfirmBar
        question={confirmText(pending)}
        onNo={() => setPending(null)}
        onYes={() => {
          setPending(null);
          onChange(pending);
        }}
      />
    );
  }
  return (
    <div className="segmented" role="radiogroup" aria-label={label}>
      {options.map((o) => (
        <button
          key={o}
          type="button"
          role="radio"
          aria-checked={o === value}
          className={`seg seg-${o}`}
          onClick={() => o !== value && (confirmText ? setPending(o) : onChange(o))}
        >
          {o.replace('_', ' ')}
        </button>
      ))}
    </div>
  );
}

export function Empty({ icon, title, children }: { icon: IconName; title: string; children?: ReactNode }) {
  return (
    <div className="empty">
      <span className="empty-icon">
        <Icon name={icon} size={22} />
      </span>
      <strong>{title}</strong>
      {children && <span className="muted">{children}</span>}
    </div>
  );
}

/** "Are you sure?" bar with No / Yes. Shared by every confirm step in the app. */
export function ConfirmBar({
  question,
  onYes,
  onNo,
  variant = 'primary',
  block,
}: {
  question: string;
  onYes: () => void;
  onNo: () => void;
  variant?: 'primary' | 'secondary' | 'danger' | 'dark';
  block?: boolean;
}) {
  return (
    <span className={`confirm ${block ? 'confirm-block' : ''}`} role="group" aria-label={question}>
      <span>{question}</span>
      <span className="confirm-actions">
        <button type="button" className="btn btn-ghost btn-sm" onClick={onNo}>
          No
        </button>
        <button type="button" className={`btn btn-sm btn-${variant}`} autoFocus onClick={onYes}>
          Yes
        </button>
      </span>
    </span>
  );
}

/** Two-step button: first click asks, second click (Yes) runs the action. Nothing is sent before Yes (a2: ui layer). */
export function ConfirmButton({
  label,
  question,
  onConfirm,
  disabled,
  variant = 'primary',
  block,
}: {
  label: ReactNode;
  question: string;
  onConfirm: () => void;
  disabled?: boolean;
  variant?: 'primary' | 'secondary' | 'danger' | 'dark';
  block?: boolean;
}) {
  const [asking, setAsking] = useState(false);
  if (!asking) {
    return (
      <button type="button" className={`btn btn-${variant} ${block ? 'btn-block' : ''}`} disabled={disabled} onClick={() => setAsking(true)}>
        {label}
      </button>
    );
  }
  return (
    <ConfirmBar
      question={question}
      variant={variant}
      block={block}
      onNo={() => setAsking(false)}
      onYes={() => {
        setAsking(false);
        onConfirm();
      }}
    />
  );
}

/** Same confirm step for any other control (e.g. a Switch): render it with `ask`, and `onConfirm` runs only after Yes. */
export function ConfirmChange({ question, onConfirm, children }: { question: string; onConfirm: () => void; children: (ask: () => void) => ReactNode }) {
  const [asking, setAsking] = useState(false);
  if (asking) {
    return (
      <ConfirmBar
        question={question}
        onNo={() => setAsking(false)}
        onYes={() => {
          setAsking(false);
          onConfirm();
        }}
      />
    );
  }
  return <>{children(() => setAsking(true))}</>;
}

/** Typographic stand-in for a restaurant logo: initials on the restaurant's colour. */
export function Monogram({ name, hue, size = 'md' }: { name: string; hue: number; size?: 'sm' | 'md' | 'lg' }) {
  return (
    <span className={`monogram monogram-${size}`} style={{ '--hue': hue } as CSSProperties} aria-hidden="true">
      {initials(name)}
    </span>
  );
}

/** Menu item thumbnail: the discount is the hero, not a food picture. Items without a discount get a neutral tile. */
export function DiscountTile({ price, originalPrice, soldOut, small }: { price: number; originalPrice: number; soldOut?: boolean; small?: boolean }) {
  const off = originalPrice > price ? Math.round((1 - price / originalPrice) * 100) : 0;
  return (
    <span className={`discount-tile ${small ? 'is-small' : ''} ${soldOut ? 'is-out' : ''}`} aria-hidden="true">
      {soldOut ? (
        <span className="discount-out">Sold out</span>
      ) : (
        off > 0 ? (
          <>
            <span className="discount-num">−{off}%</span>
            {!small && <span className="discount-off">off</span>}
          </>
        ) : (
          <span className="discount-num">Deal</span>
        )
      )}
    </span>
  );
}

// ---------- Data loading ----------

/** Loads data on mount (and when deps change); load errors are logged to the console panel. */
export function useLoad<T>(load: () => Promise<T>, deps: DependencyList) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState('');
  const reload = useCallback(() => {
    return load().then(
      (result) => {
        setData(result);
        setError('');
      },
      (e) => {
        logError(errorMessage(e));
        setError(errorMessage(e));
      },
    );
  }, deps);
  useEffect(() => {
    reload();
  }, [reload]);
  return { data, error, reload };
}

export function LoadError({ what, message, onRetry }: { what: string; message: string; onRetry: () => void }) {
  return (
    <div className="empty" role="alert">
      <span className="empty-icon">
        <Icon name="alert" size={22} />
      </span>
      <strong>We couldn't load {what}</strong>
      <span className="muted">{message}</span>
      <button className="btn btn-secondary btn-sm" onClick={onRetry}>
        Try again
      </button>
    </div>
  );
}

export function Loading({ what }: { what: string }) {
  return (
    <div className="loading" role="status">
      <span className="spinner" aria-hidden="true" /> Loading {what}…
    </div>
  );
}

// ---------- Orders ----------

const TRACK: { status: OrderStatus; label: string }[] = [
  { status: 'placed', label: 'Placed' },
  { status: 'accepted', label: 'Accepted' },
  { status: 'preparing', label: 'Preparing' },
  { status: 'ready', label: 'Ready' },
  { status: 'picked_up', label: 'On the way' },
  { status: 'delivered', label: 'Delivered' },
];

/** DoorDash-style tracker for orders on the happy path. */
export function OrderTracker({ status }: { status: OrderStatus }) {
  const current = TRACK.findIndex((s) => s.status === status);
  if (current < 0) return null;
  return (
    <ol className="tracker" aria-label={`Order progress: ${TRACK[current].label}`}>
      {TRACK.map((s, i) => (
        <li key={s.status} className={i < current ? 'done' : i === current ? 'current' : ''}>
          <span className="tracker-dot" aria-hidden="true" />
          <span className="tracker-label">{s.label}</span>
        </li>
      ))}
    </ol>
  );
}

export function OrderCard({
  order,
  title,
  subtitle,
  tracker,
  media,
  style,
  children,
}: {
  order: Order;
  title: string;
  subtitle?: ReactNode;
  tracker?: boolean;
  /** Extra visual under the tracker, e.g. a live map. */
  media?: ReactNode;
  style?: CSSProperties;
  children?: ReactNode;
}) {
  return (
    <article className={`order rise ${order.status === 'placed' ? 'is-new' : ''}`} style={style}>
      <div className="order-head">
        <div className="order-title">
          <span className="order-id">#{order.id}</span>
          <strong>{title}</strong>
          <StatusBadge status={order.status} />
        </div>
        <span className="order-meta">
          {order.createdAt && `${minutesSince(order.createdAt)} · `}
          <strong>{money(order.total)}</strong>
        </span>
      </div>
      {subtitle && <div className="order-sub">{subtitle}</div>}
      {tracker && <OrderTracker status={order.status} />}
      {media}
      <ul className="order-lines">
        {order.lines.map((l) => (
          <li key={l.itemId}>
            <span className="qty-pill">{l.quantity}×</span> {l.name}
          </li>
        ))}
      </ul>
      {order.specialInstructions && <p className="note">“{order.specialInstructions}”</p>}
      {order.rejectReason && <p className="alert alert-error">Rejected: {order.rejectReason}</p>}
      {children && <div className="order-actions">{children}</div>}
    </article>
  );
}
