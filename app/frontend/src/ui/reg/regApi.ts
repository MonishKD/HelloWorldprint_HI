// regApi (a2: ui.reg): register, log in, log out, and read who is signed in.
// The session itself is Spring Security's cookie; the UI never decides who is logged in or what role they have.
import type { Role, User } from '../../domain';
import { toSessionUser } from '../adapters';
import { request } from '../http';

/** What the UI keeps after login (a2: Session = user and role). */
export type SessionUser = Omit<User, 'password'>;
export type RegisterRole = Exclude<Role, 'operator'>;

/** GET /auth/session: who the session cookie belongs to. A 401 means nobody is signed in. */
export async function currentSession(): Promise<SessionUser> {
  return toSessionUser(await request('/auth/session'));
}

/** POST /auth/login { usernameOrEmail, password } starts the session; the session read then says who it is. */
export async function login(usernameOrEmail: string, password: string): Promise<SessionUser> {
  await request('/auth/login', { method: 'POST', body: { usernameOrEmail: usernameOrEmail.trim(), password } });
  return currentSession();
}

/**
 * POST /auth/register, then log in: registering doesn't start a session. A restaurant account comes with its own
 * closed restaurant, so only that role sends a restaurant name and address. The backend refuses operator and taken names.
 * Every role sends where it is (`latitude`, `longitude`, #244); a customer also sends their `name` and `deliveryAddress`
 * (not `address`: the backend keeps that key for restaurants and refuses it on a customer).
 */
export async function register(input: {
  username: string;
  email: string;
  password: string;
  role: RegisterRole;
  latitude: number;
  longitude: number;
  restaurantName?: string;
  name?: string;
  address?: string;
}): Promise<SessionUser> {
  const byRole =
    input.role === 'restaurant'
      ? { restaurantName: input.restaurantName, address: input.address }
      : input.role === 'customer'
        ? { name: input.name?.trim(), deliveryAddress: input.address?.trim() }
        : {};
  await request('/auth/register', {
    method: 'POST',
    body: {
      username: input.username.trim(),
      email: input.email.trim(),
      password: input.password,
      role: input.role,
      latitude: input.latitude,
      longitude: input.longitude,
      ...byRole,
    },
  });
  return login(input.username, input.password);
}

/** POST /auth/logout ends the session. */
export async function logout(): Promise<void> {
  await request('/auth/logout', { method: 'POST' });
}
