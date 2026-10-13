import { db } from '../../mockData';
import type { Role, User } from '../../domain';

/** What the UI keeps after login (a2: Session = user, role, token). `token` comes from the backend's login response. */
export type SessionUser = Omit<User, 'password'> & { token?: string };
export type RegisterRole = Exclude<Role, 'operator'>;

// ponytail: no geocoding, new accounts are dropped near downtown Montreal.
// The real backend should geocode the address (e.g. with Nominatim) instead.
function roughLocation() {
  return { lat: 45.5017 + (Math.random() - 0.5) * 0.03, lng: -73.5673 + (Math.random() - 0.5) * 0.04 };
}

function toSession({ password: _password, ...user }: User): SessionUser {
  return { ...user };
}

// TODO: replace with fetch() to the real backend: POST /auth/login
export async function login(email: string, password: string): Promise<SessionUser> {
  const user = db.users.find((u) => u.email === email.trim().toLowerCase() && u.password === password);
  if (!user) throw new Error('Invalid email or password.');
  return toSession(user);
}

// TODO: replace with fetch() to the real backend: POST /auth/register
export async function register(input: {
  name: string;
  email: string;
  password: string;
  role: RegisterRole;
  restaurantName?: string;
  address?: string;
}): Promise<SessionUser> {
  const email = input.email.trim().toLowerCase();
  if (db.users.some((u) => u.email === email)) throw new Error('An account with this email already exists.');

  const id = `u-${Date.now().toString(36)}`;
  const user: User = { id, name: input.name.trim(), email, password: input.password, role: input.role };
  if (input.role === 'restaurant') {
    user.restaurantId = `r-${id}`;
    db.restaurants.push({
      id: user.restaurantId,
      name: input.restaurantName!.trim(),
      cuisine: 'New on FortunateGoods',
      address: input.address?.trim() ?? '',
      status: 'open',
      ...roughLocation(),
    });
  }
  if (input.role === 'customer') Object.assign(user, { address: input.address?.trim() }, roughLocation());
  if (input.role === 'courier') user.courierState = 'offline';
  db.users.push(user);
  return toSession(user);
}

// TODO: replace with fetch() to the real backend: POST /auth/logout
export async function logout(_userId: string): Promise<void> {
  // Mock: nothing to invalidate. The real backend should end the session / revoke the token.
}

// TODO: replace with fetch() to the real backend: GET /couriers
export async function listCouriers(): Promise<SessionUser[]> {
  return db.users.filter((u) => u.role === 'courier').map(toSession);
}

// TODO: replace with fetch() to the real backend: PATCH /couriers/{id}/availability
export async function setCourierOnline(courierId: string, online: boolean): Promise<SessionUser> {
  const courier = db.users.find((u) => u.id === courierId && u.role === 'courier');
  if (!courier) throw new Error('Courier not found.');
  courier.courierState = online ? 'idle' : 'offline';
  return toSession(courier);
}
