import { db } from '../../mockData';
import type { Role, User } from '../../domain';

/** What the UI keeps after login (a2: Session = user and role). The backend keeps the session itself in a cookie. */
export type SessionUser = Omit<User, 'password'>;
export type RegisterRole = Exclude<Role, 'operator'>;
const PUBLIC_ROLES: Role[] = ['customer', 'restaurant', 'courier'];

// ponytail: no geocoding, new accounts are dropped near downtown Montreal.
// The real backend should geocode the address (e.g. with Nominatim) instead.
function roughLocation() {
  return { lat: 45.5017 + (Math.random() - 0.5) * 0.03, lng: -73.5673 + (Math.random() - 0.5) * 0.04 };
}

function toSession({ password: _password, ...user }: User): SessionUser {
  return { ...user };
}

// TODO: replace with fetch() to the real backend: POST /auth/login  { usernameOrEmail, password }
export async function login(usernameOrEmail: string, password: string): Promise<SessionUser> {
  const id = usernameOrEmail.trim().toLowerCase();
  const user = db.users.find((u) => (u.email === id || u.username.toLowerCase() === id) && u.password === password);
  if (!user) throw new Error('Invalid username/email or password.');
  return toSession(user);
}

// TODO: replace with fetch() to the real backend: POST /auth/register
// { username, email, password, role, restaurantName?, address? }. A restaurant is created together with its owner account.
export async function register(input: {
  username: string;
  name: string;
  email: string;
  password: string;
  role: RegisterRole;
  restaurantName?: string;
  address?: string;
}): Promise<SessionUser> {
  // The operator is seeded, never registered (R-REG-01). The form doesn't offer it; the backend must refuse it too.
  if (!PUBLIC_ROLES.includes(input.role)) throw new Error('That role cannot be registered.');
  const email = input.email.trim().toLowerCase();
  const username = input.username.trim();
  if (db.users.some((u) => u.username.toLowerCase() === username.toLowerCase())) throw new Error('That username is already taken.');
  if (db.users.some((u) => u.email === email)) throw new Error('An account with this email already exists.');

  const id = `u-${Date.now().toString(36)}`;
  const user: User = { id, username, name: input.name.trim(), email, password: input.password, role: input.role };
  if (input.role === 'restaurant') {
    user.restaurantId = `r-${id}`;
    db.restaurants.push({
      id: user.restaurantId,
      name: input.restaurantName!.trim(),
      cuisine: '', // sign-up doesn't ask for it
      address: input.address?.trim() ?? '',
      status: 'closed', // a new restaurant has no menu yet, so customers see it but can't order (R-ORD-02)
      // no lat/lng: the real sign-up doesn't ask for coordinates, so a new restaurant has no map pin until it is geocoded
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
