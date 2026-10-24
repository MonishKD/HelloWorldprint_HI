import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import type { Role } from './domain';
import { setUnauthorizedHandler } from './ui/http';
import { clearLogs } from './ui/dashboard/dashboardApi';
import { currentSession, type SessionUser } from './ui/reg/regApi';

// The session is Spring Security's cookie (see ui/http.ts); this only remembers who it belongs to.
// On load it asks the backend, so a page reload keeps you signed in.
interface Session {
  user: SessionUser | null;
  /** Message for the login page after signing out or losing the session. */
  notice: string;
  setUser: (user: SessionUser | null, notice?: string) => void;
}

const SessionContext = createContext<Session | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [user, setUserState] = useState<SessionUser | null>(null);
  const [notice, setNotice] = useState('');
  const [checked, setChecked] = useState(false);
  const setUser = (next: SessionUser | null, message = '') => {
    if (!next) clearLogs(); // logout or expiry: the next person to sign in must not see this user's console
    setUserState(next);
    setNotice(message);
  };
  // A 401 from the backend means the session is gone (R-REG-02): back to the login page with a reason.
  useEffect(() => setUnauthorizedHandler(() => setUser(null, 'Your session expired. Please log in again.')), []);
  // Is the cookie still signed in? Nothing renders until the backend answers, so no page flashes the login screen.
  useEffect(() => {
    currentSession()
      .then(setUserState, () => {})
      .finally(() => setChecked(true));
  }, []);
  if (!checked) return null;
  return <SessionContext.Provider value={{ user, notice, setUser }}>{children}</SessionContext.Provider>;
}

export function useSession(): Session {
  const session = useContext(SessionContext);
  if (!session) throw new Error('useSession must be used inside <SessionProvider>');
  return session;
}

/** Signed-in user, for components that only render behind <RequireRole>. */
export function useUser(): SessionUser {
  const { user } = useSession();
  if (!user) throw new Error('No signed-in user');
  return user;
}

export function dashboardPath(role: Role): string {
  return `/${role}`;
}
