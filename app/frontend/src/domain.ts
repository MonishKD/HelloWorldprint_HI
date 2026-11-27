// Domain types shared by the whole frontend.
// Names and enums follow the domain model (diagrams/domainModel.puml); these are the shapes the real
// backend should return. Each *Api.ts stub has a TODO naming the endpoint that returns them.
export type Role = 'customer' | 'restaurant' | 'courier' | 'operator';
export type RestaurantStatus = 'open' | 'paused' | 'closed';
export type MenuItemStatus = 'available' | 'sold_out';
export type CourierState = 'offline' | 'idle' | 'assigned' | 'carrying';
export type OrderStatus =
  | 'placed'
  | 'accepted'
  | 'preparing'
  | 'ready'
  | 'picked_up'
  | 'delivered'
  | 'rejected'
  | 'cancelled'
  | 'refunded'; // reserved: the Phase 2 doc lists it as a future state, so no screen uses it yet

export interface User {
  id: string;
  /** Login name (domain model: User.username). `name` is the display name (Customer.name, Courier.name). */
  username: string;
  name: string;
  email: string;
  password: string;
  role: Role;
  restaurantId?: string;
  address?: string;
  lat?: number;
  lng?: number;
  courierState?: CourierState;
}

/** Public map data for a courier (domain model: Courier.latPosition / longPosition / status). */
export interface CourierLocation {
  id: string;
  name: string;
  lat: number;
  lng: number;
  courierState: CourierState;
}

/** What a courier sees of an order: where and what, never prices or payment details. */
export interface CourierJob {
  orderId: string;
  status: OrderStatus;
  restaurantName: string;
  pickupAddress: string;
  pickupLat?: number;
  pickupLng?: number;
  /** The customer, or the shelter when the order is a donation. */
  destinationName: string;
  destinationAddress: string;
  destinationLat?: number;
  destinationLng?: number;
  donation: boolean;
  lines: { name: string; quantity: number }[];
  specialInstructions: string;
  pickedUpAt?: string;
}

/** Everything the courier dashboard shows: a courier holds at most one offer or one order (R-DLV-03). */
export interface CourierDashboard {
  state: CourierState;
  offer: CourierJob | null;
  order: CourierJob | null;
}

/** An order any restaurant has ready, for the courier's overview. Who takes it is decided by the dispatch rule (R-DLV-02). */
export interface ReadyOrder {
  orderId: string;
  restaurantName: string;
  pickupAddress: string;
  itemCount: number;
  readyAt: string;
}

/** One row of a courier's history (R-PRC-04): deliveries they carried, with no money fields. */
export interface CourierDelivery {
  orderId: string;
  restaurantName: string;
  destinationName: string;
  deliveredAt: string;
}

export interface Restaurant {
  id: string;
  name: string;
  cuisine: string;
  address: string;
  status: RestaurantStatus;
  /** Empty until the restaurant is geocoded: sign-up doesn't ask for coordinates, so a new restaurant has none. */
  lat?: number;
  lng?: number;
  /** Hue (0-360) for the card cover gradient. */
  hue?: number;
  rating?: number;
  distanceKm?: number;
  pickupWindow?: string;
}

/** A restaurant in the city read (#268), with its order counts by stage. */
export type CityRestaurant = Restaurant & { queued: number; inPreparation: number; ready: number; soldOutItems: string[] };

/** The state of the city (#268): no customer or payment data, so every role can read it. Only on-duty couriers are in it. */
export interface CityView {
  restaurants: CityRestaurant[];
  couriers: CourierLocation[];
}

/** An order in the dispatch queue: ready, with no courier to take it yet (R-DLV-04). */
export interface WaitingOrder {
  orderId: string;
  restaurantName: string;
  waitingSince: string;
}

/** Accepted and not picked up, and the courier went offline (#219). Only an operator can reassign it. */
export interface StalledOrder {
  orderId: string;
  restaurantName: string;
  courierName: string;
}

export interface MenuItem {
  id: string;
  restaurantId: string;
  name: string;
  description: string;
  originalPrice: number;
  price: number;
  status: MenuItemStatus;
}

export interface OrderLine {
  itemId: string;
  name: string;
  price: number;
  originalPrice: number;
  quantity: number;
}

export interface Order {
  id: string;
  customerId: string;
  customerName: string;
  deliveryAddress: string;
  /** Empty when the address hasn't been geocoded; the order is then shown without a map. */
  deliveryLat?: number;
  deliveryLng?: number;
  restaurantId: string;
  restaurantName: string;
  lines: OrderLine[];
  specialInstructions: string;
  status: OrderStatus;
  total: number;
  createdAt: string;
  rejectReason?: string;
  courierId?: string;
  courierName?: string;
  /** When the courier collected the order; drives the live map on the demo. */
  pickedUpAt?: string;
  deliveredAt?: string;
  /** When the restaurant accepted the order; the preparation queue is ordered by this (first accepted, first prepared). */
  acceptedAt?: string;
}

// ---------- Pricing (PRC) ----------

/** What an order costs, in dollars (R-PRC-01). The backend sends cents; adapters.ts converts. */
export interface Fare {
  subtotal: number;
  deliveryFee: number;
  serviceFee: number;
  surcharge: number;
  total: number;
}

/** The amount held on the card at checkout (#259), before the order is placed. */
export interface Checkout {
  paymentId: string;
  clientSecret: string;
  fare: Fare;
}

/** A delivered order's summary (R-PRC-02, #262): what was charged, line by line. */
export interface OrderSummary {
  orderId: string;
  restaurantName: string;
  courierName?: string;
  deliveryAddress: string;
  lines: { name: string; quantity: number; price: number }[];
  fare: Fare;
  placedAt: string;
  deliveredAt: string;
  cardBrand?: string;
  cardLast4?: string;
}

export type LedgerEntryType = 'charge' | 'no_charge' | 'refund';

/** One ledger row of a history (R-PRC-04, #264): customers, restaurants and the operator. A refund's amounts are negative. */
export interface LedgerRow {
  orderId: string;
  restaurantName: string;
  entryType: LedgerEntryType;
  finalStatus: OrderStatus;
  fare: Fare;
  placedAt: string;
  finishedAt: string;
  /** The Stripe PaymentIntent (pi_…) behind the order. Only set once the backend sends `paymentReference`, see prcApi. */
  paymentId?: string;
}

/** A history row: ledger rows with the fare, or a courier's deliveries with no money (R-PRC-04). */
export type HistoryRow = LedgerRow | CourierDelivery;

// ---------- Party mode (phase4-party) ----------

/** gathering: friends join and pick what they eat · roulette: everyone has to accept the card roulette · paying · placed. */
export type PartyStatus = 'gathering' | 'roulette' | 'paying' | 'placed' | 'cancelled';

export interface PartyMember {
  id: string;
  name: string;
  host: boolean;
  /** What this member pays, set by the backend once the host locks the split or the roulette picks a payer. */
  share: number;
  paid: boolean;
  acceptedRoulette: boolean;
}

/**
 * One cart line. Each of its `quantity` pieces is split evenly between the members who claimed that piece, like
 * splitting a bill: in a party of n, a piece goes down to 1/n of its price. Nobody on a piece: the host pays it.
 */
export interface PartyLine {
  itemId: string;
  name: string;
  /** Price of one piece. */
  price: number;
  quantity: number;
  /** Per piece (`quantity` of them), the ids of the members sharing it. */
  pieces: string[][];
}

/** items: each pays for the pieces they picked · even: everyone pays the same. */
export type PartySplit = 'items' | 'even';

/** A shared order: placed under the host's name once every share is paid. */
export interface Party {
  code: string;
  restaurantId: string;
  restaurantName: string;
  hostId: string;
  deliveryAddress: string;
  status: PartyStatus;
  members: PartyMember[];
  lines: PartyLine[];
  fare: Fare;
  /** How the host locked the split; unset before the lock and for the roulette. */
  split?: PartySplit;
  /** Set when the roulette picked who pays everything. */
  roulettePayerId?: string;
  /** The order the backend placed once everyone paid. */
  orderId?: string;
}

/** The card step for one member's share. */
export interface PartyCheckout {
  amount: number;
  /** Stripe client secret for the share; the stub has none. */
  clientSecret?: string;
}
