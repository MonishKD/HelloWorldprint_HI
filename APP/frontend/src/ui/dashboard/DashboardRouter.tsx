import type { ReactNode } from 'react';
import { Navigate, NavLink, useLocation } from 'react-router-dom';
import type { Role } from '../../domain';
import { dashboardPath, useSession, useUser } from '../../session';
import { initials, Logo, ThemeToggle } from '../common';
import { logout as logoutApi } from '../reg/regApi';
import { clearLogs, send } from './dashboardApi';
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

export function DashboardLayout({ nav = [], children }: { nav?: NavItem[]; children: ReactNode }) {
  const { setUser } = useSession();
  const user = useUser();
  const { pathname } = useLocation();

  async function logout() {
    // Goes through send() like every other action; the session ends even if the backend call fails.
    try {
      await send('Log out', () => logoutApi(user.id));
    } catch {
      // already shown in the output console
    }
    setUser(null);
    clearLogs();
  }

  return (
    <div className="app">
      <header className="topbar">
        <NavLink to={dashboardPath(user.role)} className="topbar-logo" aria-label="Home">
          <Logo />
        </NavLink>
        {nav.length > 0 && (
          <nav className="tabs" aria-label="Sections">
            {nav.map((n) => (
              <NavLink
                key={n.to}
                to={n.to}
                className={({ isActive }) => ((n.activeFor ? n.activeFor(pathname) : isActive) ? 'tab active' : 'tab')}
              >
                {n.label}
              </NavLink>
            ))}
          </nav>
        )}
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
        <main className="dash-main">{children}</main>
        <OutputConsolePanel />
      </div>
    </div>
  );
}
