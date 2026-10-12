import { useState, type ReactNode } from 'react';

export const money = (n: number) => n.toLocaleString('en-CA', { style: 'currency', currency: 'CAD' });

export const timeOf = (iso: string) => new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

export function Logo() {
  return (
    <span className="logo">
      <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
        <path fill="currentColor" d="M20 3c-9 0-15 4-15 11 0 2 .6 3.6 1.6 4.9L4 21.5 5.5 23l2.6-2.6C9.4 21.4 11 22 13 22c7 0 9-8 7-19Zm-7 17c-1.3 0-2.5-.3-3.4-.9 2.4-3.7 5-6.2 8.4-8.1-3.9 1.3-7 3.8-9.8 7.6-.5-.9-.7-1.9-.7-3.1 0-5.6 4.6-8.8 11.6-9.4.8 7.6-.9 13.9-6.1 13.9Z" />
      </svg>
      FortunateGoods
    </span>
  );
}

export function StatusBadge({ status }: { status: string }) {
  return <span className={`badge badge-${status}`}>{status.replace('_', ' ')}</span>;
}

/** Two-step button: first click asks, second click (Yes) runs the action. */
export function ConfirmButton({
  label,
  question,
  onConfirm,
  disabled,
  variant = 'primary',
}: {
  label: ReactNode;
  question: string;
  onConfirm: () => void;
  disabled?: boolean;
  variant?: 'primary' | 'secondary' | 'danger';
}) {
  const [asking, setAsking] = useState(false);
  if (!asking) {
    return (
      <button type="button" className={`btn btn-${variant}`} disabled={disabled} onClick={() => setAsking(true)}>
        {label}
      </button>
    );
  }
  return (
    <span className="confirm" role="group" aria-label={question}>
      <span>{question}</span>
      <button
        type="button"
        className={`btn btn-sm btn-${variant}`}
        autoFocus
        onClick={() => {
          setAsking(false);
          onConfirm();
        }}
      >
        Yes
      </button>
      <button type="button" className="btn btn-sm btn-ghost" onClick={() => setAsking(false)}>
        No
      </button>
    </span>
  );
}
