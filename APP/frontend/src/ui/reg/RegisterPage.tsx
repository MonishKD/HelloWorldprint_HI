import { useState, type FormEvent } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { dashboardPath, useSession } from '../../session';
import { Logo } from '../common';
import { errorMessage, send } from '../dashboard/dashboardApi';
import { register, type RegisterRole } from './regApi';

const ROLES: { value: RegisterRole; label: string; hint: string }[] = [
  { value: 'customer', label: 'Customer', hint: 'Order surplus food' },
  { value: 'restaurant', label: 'Restaurant', hint: 'Sell unsold food' },
  { value: 'courier', label: 'Courier', hint: 'Deliver orders' },
];

export default function RegisterPage() {
  const { user, setUser } = useSession();
  const [role, setRole] = useState<RegisterRole>('customer');
  const [name, setName] = useState('');
  const [restaurantName, setRestaurantName] = useState('');
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
      setUser(await send(`Register ${role} ${email}`, () => register({ name, email, password, role, restaurantName })));
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  }

  return (
    <main className="auth">
      <form className="card auth-card" onSubmit={onSubmit}>
        <Logo />
        <h1>Create your account</h1>

        <fieldset className="role-picker">
          <legend>I am a…</legend>
          {ROLES.map((r) => (
            <label key={r.value} className={role === r.value ? 'role selected' : 'role'}>
              <input type="radio" name="role" value={r.value} checked={role === r.value} onChange={() => setRole(r.value)} />
              <strong>{r.label}</strong>
              <small>{r.hint}</small>
            </label>
          ))}
        </fieldset>

        <label>
          Your name
          <input required pattern=".*\S.*" autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} />
        </label>
        {role === 'restaurant' && (
          <label>
            Restaurant name
            <input required pattern=".*\S.*" value={restaurantName} onChange={(e) => setRestaurantName(e.target.value)} />
          </label>
        )}
        <label>
          Email
          <input type="email" required autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label>
          Password <small className="muted">(at least 6 characters)</small>
          <input type="password" required minLength={6} autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>

        {error && <p className="alert alert-error" role="alert">{error}</p>}

        <button className="btn btn-primary btn-block" disabled={busy}>
          {busy ? 'Creating account…' : 'Create account'}
        </button>
        <p className="muted center">
          Already have an account? <Link to="/login">Log in</Link>
        </p>
      </form>
    </main>
  );
}
