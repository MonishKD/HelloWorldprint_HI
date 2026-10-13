import { useState, type FormEvent } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { dashboardPath, useSession } from '../../session';
import { errorMessage, send } from '../dashboard/dashboardApi';
import { Icon, type IconName } from '../icons';
import AuthShell from './AuthShell';
import { register, type RegisterRole } from './regApi';

const ROLES: { value: RegisterRole; label: string; hint: string; icon: IconName }[] = [
  { value: 'customer', label: 'Customer', hint: 'Order surplus food', icon: 'bag' },
  { value: 'restaurant', label: 'Restaurant', hint: 'Sell what’s left', icon: 'store' },
  { value: 'courier', label: 'Courier', hint: 'Deliver & earn', icon: 'bike' },
];

export default function RegisterPage() {
  const { user, setUser } = useSession();
  const [role, setRole] = useState<RegisterRole>('customer');
  const [name, setName] = useState('');
  const [restaurantName, setRestaurantName] = useState('');
  const [address, setAddress] = useState('');
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
      setUser(await send(`Register ${role} ${email}`, () => register({ name, email, password, role, restaurantName, address })));
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  }

  return (
    <AuthShell>
      <form className="auth-form rise" onSubmit={onSubmit}>
        <div>
          <h1>Create your account</h1>
          <p className="muted">Join the fight against food waste in under a minute.</p>
        </div>

        <fieldset className="role-picker">
          <legend className="eyebrow">I want to…</legend>
          {ROLES.map((r) => (
            <label key={r.value} className={role === r.value ? 'role selected' : 'role'}>
              <input type="radio" name="role" value={r.value} checked={role === r.value} onChange={() => setRole(r.value)} />
              <span className="role-icon">
                <Icon name={r.icon} size={22} />
              </span>
              <strong>{r.label}</strong>
              <small>{r.hint}</small>
            </label>
          ))}
        </fieldset>

        <label className="field">
          <span>Your name</span>
          <input required pattern=".*\S.*" autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} />
        </label>
        {role === 'restaurant' && (
          <label className="field">
            <span>Restaurant name</span>
            <input required pattern=".*\S.*" value={restaurantName} onChange={(e) => setRestaurantName(e.target.value)} />
          </label>
        )}
        {role !== 'courier' && (
          <label className="field">
            <span>{role === 'restaurant' ? 'Restaurant address' : 'Delivery address'}</span>
            <input required pattern=".*\S.*" autoComplete="street-address" value={address} onChange={(e) => setAddress(e.target.value)} />
          </label>
        )}
        <label className="field">
          <span>Email</span>
          <input type="email" required autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label className="field">
          <span>
            Password <small className="muted">· at least 6 characters</small>
          </span>
          <input type="password" required minLength={6} autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>

        {error && (
          <p className="alert alert-error" role="alert">
            {error}
          </p>
        )}

        <button className="btn btn-primary btn-lg btn-block" disabled={busy}>
          {busy ? 'Creating account…' : 'Create account'}
        </button>
        <p className="muted center">
          Already have an account? <Link to="/login">Log in</Link>
        </p>
      </form>
    </AuthShell>
  );
}
