import type { ReactNode } from 'react';
import { Navigate, NavLink } from 'react-router-dom';
import type { Role } from '../../mockData';
import { dashboardPath, useSession, useUser } from '../../session';
import { Logo } from '../common';
import { clearLogs, logSent } from './dashboardApi';

/** Catch-all route: send users to their own dashboard, or to login when signed out. */
export default function DashboardRouter() {
  const { user } = useSession();
  return <Navigate to={user ? dashboardPath(user.role) : '/login'} replace />;
}

/** Guards a dashboard: signed-out users go to login, other roles go back to their own dashboard. */
export function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { user } = useSession();
  if (!user) return <Navigate to="/login" replace />;
  if (!roles.includes(user.role)) return <Navigate to={dashboardPath(user.role)} replace />;
  return <>{children}</>;
}

function useLogout() {
  const { user, setUser } = useSession();
  return () => {
    logSent(`Log out ${user?.email}`);
    setUser(null);
    clearLogs();
  };
}

export function DashboardLayout({
  title,
  nav = [],
  children,
}: {
  title: string;
  nav?: { to: string; label: string; end?: boolean }[];
  children: ReactNode;
}) {
  const user = useUser();
  const logout = useLogout();
  return (
    <div className="app">
      <header className="topbar">
        <Logo />
        <nav className="tabs">
          {nav.map((n) => (
            <NavLink key={n.to} to={n.to} end={n.end} className="tab">
              {n.label}
            </NavLink>
          ))}
        </nav>
        <div className="who">
          <span>
            {user.name} <small className="muted">· {title}</small>
          </span>
          <button className="btn btn-sm btn-ghost" onClick={logout}>
            Log out
          </button>
        </div>
      </header>
      <div className="dash">
        <main className="dash-main">{children}</main>
      </div>
    </div>
  );
}

export function ComingSoonPage() {
  const user = useUser();
  const logout = useLogout();
  return (
    <main className="auth">
      <div className="card auth-card center">
        <Logo />
        <h1>Coming soon</h1>
        <p className="muted">
          Hi {user.name}! The {user.role} dashboard isn't part of this version yet. Thanks for helping reduce food waste.
        </p>
        <button className="btn btn-secondary btn-block" onClick={logout}>
          Log out
        </button>
      </div>
    </main>
  );
}
