import type { ReactNode } from 'react';
import { Link, Navigate, NavLink, useLocation } from 'react-router-dom';
import type { Role } from '../../domain';
import { dashboardPath, useSession, useUser } from '../../session';
import { initials, Logo, ThemeToggle } from '../common';
import AuthShell from '../reg/AuthShell';
import { logout as logoutApi } from '../reg/regApi';
import { send } from './dashboardApi';
import InformationArea, { DashboardInfoProvider } from './InformationArea';
import OutputConsolePanel from './OutputConsolePanel';

/** Catch-all route: send users to their own dashboard, or to login when signed out. */
export default function DashboardRouter() {
  const { user } = useSession();
  return <Navigate to={user ? dashboardPath(user.role) : '/login'} replace />;
}

/** Guards a dashboard: signed-out users go to login, other roles go back to their own dashboard. */
export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const { user } = useSession();
  const location = useLocation();
  // Remember the page (a party link, for example) so login brings them back to it.
  if (!user) return <Navigate to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`} replace />;
  // A restaurant, courier or operator who opens a party link (or QR code) is told why instead of being moved silently.
  if (user.role !== role && location.pathname.startsWith('/customer/party')) return <PartyForCustomersOnly role={user.role} />;
  if (user.role !== role) return <Navigate to={dashboardPath(user.role)} replace />;
  return <>{children}</>;
}

function PartyForCustomersOnly({ role }: { role: Role }) {
  return (
    <AuthShell>
      <div className="auth-form rise">
        <div>
          <h1>Party links are for customers</h1>
          <p className="muted">
            You're signed in with {role === 'operator' ? 'an' : 'a'} {role} account. To join this party, log out and sign in (or sign up) with a customer account, then open the link again.
          </p>
        </div>
        <Link className="btn btn-primary btn-lg btn-block" to={dashboardPath(role)}>
          Go to my dashboard
        </Link>
      </div>
    </AuthShell>
  );
}

export interface NavItem {
  to: string;
  label: string;
  /** Overrides NavLink's prefix matching when a tab should own several paths. */
  activeFor?: (pathname: string) => boolean;
}

const mapTab = (role: Role): NavItem => ({ to: `/${role}/map`, label: 'Map' });
const historyTab = (role: Role): NavItem => ({ to: `/${role}/history`, label: 'Pricing & History' });

/** Each role only gets the tabs it can use (R-DASH-01). */
const TABS: Record<Role, NavItem[]> = {
  customer: [
    mapTab('customer'),
    { to: '/customer', label: 'Orders', activeFor: (path) => !['/customer/orders', '/customer/map', '/customer/history', '/customer/party'].some((p) => path.startsWith(p)) },
    { to: '/customer/orders', label: 'Delivery' },
    { to: '/customer/party', label: 'Party' },
    historyTab('customer'),
  ],
  restaurant: [mapTab('restaurant'), { to: '/restaurant', label: 'Orders' }, historyTab('restaurant')],
  courier: [mapTab('courier'), { to: '/courier', label: 'Delivery' }, historyTab('courier')],
  operator: [mapTab('operator'), { to: '/operator', label: 'Orders' }, { to: '/operator/delivery', label: 'Delivery' }, historyTab('operator')],
};

/** The open tab: the part of the path after the dashboard, empty on the dashboard's first tab. */
export function useDashboardTab(): string {
  return useLocation().pathname.split('/')[2] ?? '';
}

export function DashboardLayout({ children }: { children: ReactNode }) {
  const { setUser } = useSession();
  const user = useUser();
  const { pathname } = useLocation();
  const nav = TABS[user.role];

  async function logout() {
    // Goes through send() like every other action; the session ends even if the backend call fails.
    try {
      await send('Log out', logoutApi);
    } catch {
      // already shown in the output console
    }
    setUser(null, 'You have been logged out.');
  }

  return (
    <DashboardInfoProvider>
    <div className="app">
      <header className="topbar">
        <NavLink to={dashboardPath(user.role)} className="topbar-logo" aria-label="Home">
          <Logo />
        </NavLink>
        <nav className="tabs" aria-label="Sections">
          {nav.map((n) => (
            <NavLink
              key={n.to}
              to={n.to}
              end
              className={({ isActive }) => ((n.activeFor ? n.activeFor(pathname) : isActive) ? 'tab active' : 'tab')}
            >
              {n.label}
            </NavLink>
          ))}
        </nav>
        <div className="who">
          <ThemeToggle />
          <span className="avatar" aria-hidden="true">
            {initials(user.name)}
          </span>
          <span className="who-text">
            <strong>{user.name}</strong>
            <small>{user.role}</small>
          </span>
          <button className="btn btn-ghost btn-sm" onClick={logout}>
            Log out
          </button>
        </div>
      </header>
      <div className="dash">
        <main className="dash-main">
          <InformationArea />
          {children}
        </main>
        <OutputConsolePanel />
      </div>
    </div>
    </DashboardInfoProvider>
  );
}
