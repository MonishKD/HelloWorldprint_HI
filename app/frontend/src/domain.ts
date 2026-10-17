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
}
