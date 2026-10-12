import { useState, type FormEvent } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { dashboardPath, useSession } from '../../session';
import { Logo } from '../common';
import { errorMessage, send } from '../dashboard/dashboardApi';
import { login } from './regApi';

// Quick-fill buttons for the seeded demo accounts (see README).
const DEMO_ACCOUNTS = [
  { label: 'Customer', email: 'alice@demo.com' },
  { label: 'Restaurant', email: 'greenleaf@demo.com' },
  { label: 'Courier', email: 'courier@demo.com' },
  { label: 'Operator', email: 'operator@demo.com' },
];

export default function LoginPage() {
  const { user, setUser } = useSession();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={dashboardPath(user.role)} replace />;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      setUser(await send(`Log in as ${email}`, () => login(email, password)));
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  }

  return (
    <main className="auth">
      <form className="card auth-card" onSubmit={onSubmit}>
        <Logo />
        <h1>Welcome back</h1>
        <p className="muted">Rescue good food from local restaurants at a discount.</p>

        <label>
          Email
          <input type="email" required autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label>
          Password
          <input type="password" required autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>

        {error && <p className="alert alert-error" role="alert">{error}</p>}

        <button className="btn btn-primary btn-block" disabled={busy}>
          {busy ? 'Logging in…' : 'Log in'}
        </button>
        <p className="muted center">
          New here? <Link to="/register">Create an account</Link>
        </p>

        <div className="demo">
          <span className="muted">Demo accounts (password demo123):</span>
          <div className="chips">
            {DEMO_ACCOUNTS.map((a) => (
              <button
                key={a.email}
                type="button"
                className="chip"
                onClick={() => {
                  setEmail(a.email);
                  setPassword('demo123');
                }}
              >
                {a.label}
              </button>
            ))}
          </div>
        </div>
      </form>
    </main>
  );
}
