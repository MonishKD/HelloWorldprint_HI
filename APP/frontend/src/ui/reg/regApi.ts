import { db, type Role, type User } from '../../mockData';

export type SessionUser = Omit<User, 'password'>;
export type RegisterRole = Exclude<Role, 'operator'>;

function toSession({ password: _password, ...user }: User): SessionUser {
  return user;
}

// TODO: replace with fetch() to the real backend
export async function login(email: string, password: string): Promise<SessionUser> {
  const user = db.users.find((u) => u.email === email.trim().toLowerCase() && u.password === password);
  if (!user) throw new Error('Invalid email or password.');
  return toSession(user);
}

// TODO: replace with fetch() to the real backend
export async function register(input: {
  name: string;
  email: string;
  password: string;
  role: RegisterRole;
  restaurantName?: string;
}): Promise<SessionUser> {
  const email = input.email.trim().toLowerCase();
  if (db.users.some((u) => u.email === email)) throw new Error('An account with this email already exists.');

  const id = `u-${Date.now().toString(36)}`;
  let restaurantId: string | undefined;
  if (input.role === 'restaurant') {
    restaurantId = `r-${id}`;
    db.restaurants.push({ id: restaurantId, name: input.restaurantName!.trim(), cuisine: 'New on FortunateGoods', address: '', status: 'open' });
  }
  const user: User = { id, name: input.name.trim(), email, password: input.password, role: input.role, restaurantId };
  db.users.push(user);
  return toSession(user);
}
