// Fake backend for the operator dashboard, until #250, #254, #266, #268, #274 and #276 land. Delete this file when
// operatorApi.ts is connected. It answers in the backend's JSON, and state is kept per browser tab.
// It is a separate city from dlvMock.ts: what happens here doesn't reach the courier dashboard.

const STORE_KEY = 'fortunategoods.operatorMock';

type Row = Record<string, any>;

interface Db {
  restaurants: Row[];
  couriers: Row[];
  orders: Row[];
}

const CUSTOMERS = {
  alice: { deliveryAddress: '1455 Boul. De Maisonneuve O, Montréal', deliveryLatitude: 45.4972, deliveryLongitude: -73.5789 },
  bob: { deliveryAddress: '4200 Rue Saint-Laurent, Montréal', deliveryLatitude: 45.5196, deliveryLongitude: -73.5835 },
};

function seed(): Db {
  const restaurants = [
    { id: '1', name: 'Poulet Rouge', status: 'OPEN', cuisine: 'Chicken', address: '1623 Rue Sainte-Catherine O, Montréal', latitude: 45.4957, longitude: -73.5795 },
    { id: '2', name: 'Uniburger', status: 'OPEN', cuisine: 'Burgers', address: '2001 Rue Saint-Denis, Montréal', latitude: 45.515, longitude: -73.5641 },
    { id: '3', name: 'Boustan', status: 'PAUSED', cuisine: 'Lebanese', address: '2020A Rue Crescent, Montréal', latitude: 45.499, longitude: -73.5776 },
    { id: '4', name: 'Lola Rosa', status: 'CLOSED', cuisine: 'Vegetarian', address: '545 Rue Milton, Montréal', latitude: 45.5072, longitude: -73.5745 },
    { id: '5', name: 'Café Myriade', status: 'CLOSED', cuisine: 'Café', address: '1432 Rue Mackay, Montréal', latitude: 45.4966, longitude: -73.5786 },
  ];
  const couriers = [
    { id: '3', name: 'carl', latitude: 45.505, longitude: -73.571, status: 'CARRYING' },
    { id: '8', name: 'eli', latitude: 45.4975, longitude: -73.58, status: 'ASSIGNED' },
    { id: '9', name: 'dina', latitude: 45.51, longitude: -73.57, status: 'OFFLINE' },
  ];

  let nextId = 2001;
  const order = (restaurantId: string, customer: keyof typeof CUSTOMERS, status: string, minutesAgo: number, courierId?: string) => {
    const restaurant = restaurants.find((r) => r.id === restaurantId)!;
    const courier = couriers.find((c) => c.id === courierId);
    const createdAt = new Date(Date.now() - minutesAgo * 60_000).toISOString();
    return {
      orderId: String(nextId++),
      restaurantId,
      restaurantName: restaurant.name,
      customerName: customer,
      ...CUSTOMERS[customer],
      lines: [{ itemId: '1', name: 'Combo', price: 14.5, quantity: 2 }],
      status,
      createdAt,
      courierId: courier?.id,
      courierName: courier?.name,
    };
  };

  return {
    restaurants,
    couriers,
    orders: [
      order('2', 'alice', 'CANCELLED', 120),
      order('1', 'bob', 'REJECTED', 110),
      order('2', 'bob', 'REFUNDED', 90, '8'),
      order('1', 'alice', 'DELIVERED', 70, '3'),
      order('2', 'alice', 'PICKED_UP', 35, '3'),
      // Stalled: dina accepted it, then went offline.
      order('2', 'alice', 'READY', 30, '9'),
      order('1', 'bob', 'READY', 28, '8'),
      // No Courier Available: two ready orders in the dispatch queue.
      order('1', 'alice', 'READY', 26),
      order('2', 'bob', 'READY', 22),
      order('1', 'bob', 'PREPARING', 20),
      // Dinner Rush: six accepted orders at one restaurant is a long queue (R-DASH-03).
      ...[18, 15, 12, 9, 6, 3].map((minutes, i) => order('2', i % 2 ? 'bob' : 'alice', 'ACCEPTED', minutes)),
      order('1', 'alice', 'PLACED', 2),
    ],
  };
}

function load(): Db {
  const saved = sessionStorage.getItem(STORE_KEY);
  return saved ? JSON.parse(saved) : seed();
}

const inQueue = (db: Db) => db.orders.filter((o) => o.status === 'READY' && !o.courierId).sort((a, b) => a.createdAt.localeCompare(b.createdAt));
const find = (db: Db, id: string) => db.orders.find((o) => o.orderId === id)!;
const count = (db: Db, restaurantId: string, status: string) => db.orders.filter((o) => o.restaurantId === restaurantId && o.status === status).length;

/** Whenever a courier frees up, the oldest waiting order goes to them (#250). Which courier doesn't matter here. */
function retryDispatch(db: Db) {
  const courier = db.couriers.find((c) => c.status === 'IDLE');
  const waiting = inQueue(db)[0];
  if (!courier || !waiting) return;
  courier.status = 'ASSIGNED';
  waiting.courierId = courier.id;
  waiting.courierName = courier.name;
}

const ROUTES: Record<string, (db: Db, id: string, body: any) => unknown> = {
  'GET /dashboard/city': (db) => ({
    restaurants: db.restaurants.map((r) => ({
      ...r,
      queued: count(db, r.id, 'ACCEPTED'),
      inPreparation: count(db, r.id, 'PREPARING'),
      ready: count(db, r.id, 'READY'),
    })),
    couriers: db.couriers.filter((c) => c.status !== 'OFFLINE'),
  }),
  'GET /operator/orders': (db) => db.orders,
  'GET /operator/dispatch-queue': (db) => inQueue(db).map((o) => ({ ...o, waitingSince: o.createdAt })),
  'GET /operator/stalled-orders': (db) =>
    db.orders.filter((o) => o.status === 'READY' && db.couriers.find((c) => c.id === o.courierId)?.status === 'OFFLINE'),
  'PATCH /restaurants/:id/status': (db, id, body) => {
    db.restaurants.find((r) => r.id === id)!.status = body.status.toUpperCase();
  },
  // An order a courier was assigned to frees them up, unless they're offline.
  'POST /orders/:id/operator-cancel': (db, id) => {
    const order = find(db, id);
    const courier = db.couriers.find((c) => c.id === order.courierId);
    order.status = 'CANCELLED';
    if (courier?.status === 'ASSIGNED') courier.status = 'IDLE';
    retryDispatch(db);
  },
  // The offline courier isn't a candidate, so with nobody idle the order waits in the dispatch queue (#219).
  'POST /orders/:id/reassign': (db, id) => {
    const order = find(db, id);
    delete order.courierId;
    delete order.courierName;
    retryDispatch(db);
  },
  'POST /orders/:id/refund': (db, id) => {
    find(db, id).status = 'REFUNDED';
  },
  'POST /operator/reset': (db) => Object.assign(db, seed()),
};

/** Same call shape as request() in ../http.ts. A successful answer has no envelope, so send() logs it to the console itself. */
export async function mockRequest<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const route = ROUTES[`${method} ${path.replace(/\d+/, ':id')}`];
  if (!route) throw new Error(`No mock for ${method} ${path}`);
  const db = load();
  const answer = route(db, path.match(/\d+/)?.[0] ?? '', options.body);
  sessionStorage.setItem(STORE_KEY, JSON.stringify(db));
  return answer as T;
}
