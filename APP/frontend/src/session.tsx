import { createContext, useContext, useState, type ReactNode } from 'react';
import type { Role } from './mockData';
import type { SessionUser } from './ui/reg/regApi';

// Fake session: held in React state only, so a page reload logs you out.
interface Session {
  user: SessionUser | null;
  setUser: (user: SessionUser | null) => void;
}

const SessionContext = createContext<Session | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(null);
  return <SessionContext.Provider value={{ user, setUser }}>{children}</SessionContext.Provider>;
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
  if (role === 'customer') return '/customer';
  if (role === 'restaurant') return '/restaurant';
  return '/coming-soon';
}
