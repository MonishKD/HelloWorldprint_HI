import type { ReactNode } from 'react';
import { Logo, ThemeToggle } from '../common';
import OrderLifecycle from '../OrderLifecycle';

/** Split-screen frame shared by the login and register pages. */
export default function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="auth">
      <aside className="auth-hero" aria-hidden="true">
        <Logo />
        <div className="auth-hero-body">
          <div className="auth-hero-copy">
            <h2>
              Good food deserves a <em>second</em> chance.
            </h2>
            <p>Restaurants list what they didn't sell today. You get it for a fraction of the price, delivered to your door.</p>
          </div>
          <OrderLifecycle />
        </div>
      </aside>
      <main className="auth-main">
        <div className="auth-top">
          <span className="auth-logo-mobile">
            <Logo />
          </span>
          <ThemeToggle />
        </div>
        {children}
      </main>
    </div>
  );
}
