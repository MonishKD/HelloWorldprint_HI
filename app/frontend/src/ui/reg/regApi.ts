import { db } from '../../mockData';
import { request } from '../http';
import type { Role, User } from '../../domain';

/** What the UI keeps after login (a2: Session = user and role). The backend keeps the session itself in a cookie. */
export type SessionUser = Omit<User, 'password'>;
export type RegisterRole = Exclude<Role, 'operator'>;

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

/** What POST /auth/register sends back (RegisterResponse on the backend). The role comes in capitals, e.g. "CUSTOMER". */
interface RegisterResponse {
  userId: string;
  username: string;
  email: string;
  role: string;
  /** Only for a restaurant account: the restaurant created with it (#144). */
  restaurantId: string | null;
}

// POST /auth/register (#60), body = RegisterReqJSON on the backend.
// The backend is the authority: it refuses operator, a taken username/email (409) and missing fields (400),
// and saves a restaurant account together with its closed restaurant and owner link (#144).
// Registering doesn't log in, so a POST /auth/login follows to start the session cookie.
export async function register(input: {
  username: string;
  email: string;
  password: string;
  role: RegisterRole;
  restaurantName?: string;
  address?: string;
}): Promise<SessionUser> {
  const account = await request<RegisterResponse>('/auth/register', {
    method: 'POST',
    body: {
      username: input.username.trim(),
      email: input.email.trim(),
      password: input.password,
      role: input.role,
      // Only a restaurant sends these; the backend refuses them for other roles.
      restaurantName: input.role === 'restaurant' ? input.restaurantName : undefined,
      address: input.role === 'restaurant' ? input.address : undefined,
    },
  });
  await request('/auth/login', { method: 'POST', body: { usernameOrEmail: account.username, password: input.password } });
  return {
    id: account.userId,
    username: account.username,
    name: account.username, // the backend has no separate display name
    email: account.email,
    role: account.role.toLowerCase() as Role,
    restaurantId: account.restaurantId ?? undefined,
  };
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
