import type { ReactNode } from 'react';
import { Navigate, NavLink, useLocation } from 'react-router-dom';
import type { Role } from '../../domain';
import { dashboardPath, useSession, useUser } from '../../session';
import { initials, Logo, ThemeToggle } from '../common';
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
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== role) return <Navigate to={dashboardPath(user.role)} replace />;
  return <>{children}</>;
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
    { to: '/customer', label: 'Orders', activeFor: (path) => !['/customer/orders', '/customer/map', '/customer/history'].some((p) => path.startsWith(p)) },
    { to: '/customer/orders', label: 'Delivery' },
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
