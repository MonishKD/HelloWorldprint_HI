import { useState, type FormEvent } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { dashboardPath, useSession } from '../../session';
import { errorMessage, send } from '../dashboard/dashboardApi';
import { Icon, type IconName } from '../icons';
import AuthShell from './AuthShell';
import { login } from './regApi';

// Quick-fill buttons for the seeded demo accounts (see README).
const DEMO_ACCOUNTS: { label: string; icon: IconName; email: string }[] = [
  { label: 'Customer', icon: 'bag', email: 'alice@demo.com' },
  { label: 'Restaurant', icon: 'store', email: 'greenleaf@demo.com' },
  { label: 'Courier', icon: 'bike', email: 'courier@demo.com' },
  { label: 'Operator', icon: 'grid', email: 'operator@demo.com' },
];

export default function LoginPage() {
  const { user, notice, setUser } = useSession();
  const [email, setEmail] = useState(''); // username or email
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
    <AuthShell>
      <form className="auth-form rise" onSubmit={onSubmit}>
        <div>
          <h1>Welcome back</h1>
          <p className="muted">Log in to keep rescuing good food.</p>
        </div>

        {notice && !error && (
          <p className="alert alert-success" role="status">
            {notice}
          </p>
        )}

        <label className="field">
          <span>Username or email</span>
          <input required autoComplete="username" autoCapitalize="none" spellCheck={false} placeholder="you@example.com" value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label className="field">
          <span>Password</span>
          <input type="password" required autoComplete="current-password" placeholder="••••••" value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>

        {error && (
          <p className="alert alert-error" role="alert">
            {error}
          </p>
        )}

        <button className="btn btn-primary btn-lg btn-block" disabled={busy}>
          {busy ? 'Logging in…' : 'Log in'}
        </button>
        <p className="muted center">
          New here? <Link to="/register">Create an account</Link>
        </p>

        <div className="demo">
          <span className="eyebrow">Try a demo account · password demo123</span>
          <div className="demo-grid">
            {DEMO_ACCOUNTS.map((a) => (
              <button
                key={a.email}
                type="button"
                className={`demo-chip ${email === a.email ? 'selected' : ''}`}
                onClick={() => {
                  setEmail(a.email);
                  setPassword('demo123');
                }}
              >
                <Icon name={a.icon} />
                {a.label}
              </button>
            ))}
          </div>
        </div>
      </form>
    </AuthShell>
  );
}
