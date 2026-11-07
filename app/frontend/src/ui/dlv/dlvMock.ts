// Fake backend for the courier routes, until #245, #248 and #252 land. Delete this file when dlvApi.ts is connected.
// It answers in the backend's JSON (uppercase enums, latitude/longitude) so adapters.ts is already exercised, and it
// follows the DLV rules a courier can see. State is kept per browser tab, so a page reload doesn't lose it.
// Not simulated: other couriers, and restaurants marking orders ready: an order is simply offered a few seconds after
// the courier becomes idle.

const STORE_KEY = 'fortunategoods.dlvMock';
const OFFER_DELAY_MS = 4000;

type Job = Record<string, unknown>;

const POULET_ROUGE = { restaurantName: 'Poulet Rouge', pickupAddress: '1623 Rue Sainte-Catherine O, Montréal', pickupLatitude: 45.4957, pickupLongitude: -73.5795 };
const UNIBURGER = { restaurantName: 'Uniburger', pickupAddress: '2001 Rue Saint-Denis, Montréal', pickupLatitude: 45.515, pickupLongitude: -73.5641 };

const JOBS: Job[] = [
  {
    ...POULET_ROUGE,
    destinationName: 'alice',
    deliveryAddress: '1455 Boul. De Maisonneuve O, Montréal',
    deliveryLatitude: 45.4972,
    deliveryLongitude: -73.5789,
    lines: [
      { name: 'Poulet Rouge bowl', quantity: 2 },
      { name: 'Lentil soup', quantity: 1 },
    ],
    instructions: 'Leave it at the front desk.',
  },
  {
    ...UNIBURGER,
    destinationName: 'Downtown Day Shelter',
    deliveryAddress: '3000 Rue Saint-Antoine O, Montréal',
    deliveryLatitude: 45.4799,
    deliveryLongitude: -73.5853,
    shelterId: 'shelter-1',
    lines: [{ name: 'Veggie burger', quantity: 4 }],
  },
  {
    ...UNIBURGER,
    destinationName: 'bob',
    deliveryAddress: '4200 Rue Saint-Laurent, Montréal',
    deliveryLatitude: 45.5196,
    deliveryLongitude: -73.5835,
    lines: [
      { name: 'Classic burger', quantity: 1 },
      { name: 'Fries', quantity: 1 },
    ],
  },
];

interface Db {
  status: 'OFFLINE' | 'IDLE' | 'ASSIGNED' | 'CARRYING';
  offer: Job | null;
  currentOrder: Job | null;
  /** How many orders were delivered, which also picks the next job. */
  delivered: number;
  offerAt: number;
  history: Job[];
}

function load(): Db {
  const saved = sessionStorage.getItem(STORE_KEY);
  return saved ? JSON.parse(saved) : { status: 'OFFLINE', offer: null, currentOrder: null, delivered: 0, offerAt: 0, history: [] };
}

/** The nth order of the simulation: orders come up in a fixed cycle of JOBS. */
const jobAt = (n: number): Job => ({ ...JOBS[n % JOBS.length], orderId: String(1042 + n), status: 'READY' });

function waitForOffer(db: Db) {
  db.status = 'IDLE';
  db.offerAt = Date.now() + OFFER_DELAY_MS;
}

const ROUTES: Record<string, (db: Db) => unknown> = {
  'GET /couriers/me': (db) => {
    if (db.status === 'IDLE' && !db.offer && Date.now() >= db.offerAt) {
      db.offer = jobAt(db.delivered);
    }
    return { status: db.status, offer: db.offer, currentOrder: db.currentOrder };
  },
  // The next orders waiting, oldest first. One this courier already holds isn't waiting any more.
  'GET /couriers/ready-orders': (db) =>
    JOBS.map((_, i) => jobAt(db.delivered + i))
      .filter((job) => job.orderId !== db.currentOrder?.orderId)
      .map((job, i) => ({
        ...job,
        itemCount: (job.lines as { quantity: number }[]).reduce((n, l) => n + l.quantity, 0),
        readyAt: new Date(Date.now() - (JOBS.length - i) * 3 * 60_000).toISOString(),
      })),
  'POST /couriers/me/on-duty': (db) => {
    if (db.currentOrder) db.status = 'ASSIGNED';
    else waitForOffer(db);
  },
  // An offer is withdrawn. An accepted order stays with the courier and is stalled until an operator reassigns it (#219).
  'POST /couriers/me/off-duty': (db) => {
    db.status = 'OFFLINE';
    db.offer = null;
  },
  'POST /couriers/me/offer/accept': (db) => {
    db.currentOrder = db.offer;
    db.offer = null;
    db.status = 'ASSIGNED';
  },
  // Back to the queue. With no other courier the rule picks this one again, so the same order returns (#216).
  'POST /couriers/me/offer/decline': (db) => {
    db.offer = null;
    waitForOffer(db);
  },
  'POST /couriers/me/order/pickup': (db) => {
    db.currentOrder = { ...db.currentOrder, status: 'PICKED_UP', pickedUpAt: new Date().toISOString() };
    db.status = 'CARRYING';
  },
  'POST /couriers/me/order/deliver': (db) => {
    db.history.push({ ...db.currentOrder, deliveredAt: new Date().toISOString() });
    db.currentOrder = null;
    db.delivered += 1;
    waitForOffer(db);
  },
  'GET /history': (db) => db.history,
};

/** Same call shape as request() in ../http.ts. A successful answer has no envelope, so send() logs it to the console itself. */
export async function mockRequest<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const route = ROUTES[`${options.method ?? 'GET'} ${path}`];
  if (!route) throw new Error(`No mock for ${options.method ?? 'GET'} ${path}`);
  const db = load();
  const answer = route(db);
  sessionStorage.setItem(STORE_KEY, JSON.stringify(db));
  return answer as T;
}
