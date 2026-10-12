// Static seed data for v1. Names follow the domain model (diagrams/domainModel.puml).
// Everything lives in memory and resets on page reload.

export type Role = 'customer' | 'restaurant' | 'courier' | 'operator';
export type RestaurantStatus = 'open' | 'paused' | 'closed';
export type MenuItemStatus = 'available' | 'sold_out';
export type OrderStatus = 'placed' | 'accepted' | 'preparing' | 'ready' | 'rejected' | 'cancelled';

export interface User {
  id: string;
  name: string;
  email: string;
  password: string;
  role: Role;
  restaurantId?: string;
}

export interface Restaurant {
  id: string;
  name: string;
  cuisine: string;
  address: string;
  status: RestaurantStatus;
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
  quantity: number;
}

export interface Order {
  id: string;
  customerId: string;
  customerName: string;
  restaurantId: string;
  restaurantName: string;
  lines: OrderLine[];
  specialInstructions: string;
  status: OrderStatus;
  total: number;
  createdAt: string;
  rejectReason?: string;
}

const minutesAgo = (n: number) => new Date(Date.now() - n * 60_000).toISOString();

const users: User[] = [
  { id: 'u-alice', name: 'Alice Martin', email: 'alice@demo.com', password: 'demo123', role: 'customer' },
  { id: 'u-bob', name: 'Bob Nguyen', email: 'bob@demo.com', password: 'demo123', role: 'customer' },
  { id: 'u-green', name: 'Green Leaf Bakery', email: 'greenleaf@demo.com', password: 'demo123', role: 'restaurant', restaurantId: 'r-green' },
  { id: 'u-sushi', name: 'Sakura Sushi', email: 'sakura@demo.com', password: 'demo123', role: 'restaurant', restaurantId: 'r-sushi' },
  { id: 'u-taco', name: 'Taco Verde', email: 'tacoverde@demo.com', password: 'demo123', role: 'restaurant', restaurantId: 'r-taco' },
  { id: 'u-carl', name: 'Carl Courier', email: 'courier@demo.com', password: 'demo123', role: 'courier' },
  { id: 'u-olga', name: 'Olga Operator', email: 'operator@demo.com', password: 'demo123', role: 'operator' },
];

const restaurants: Restaurant[] = [
  { id: 'r-green', name: 'Green Leaf Bakery', cuisine: 'Bakery & café', address: '1455 De Maisonneuve Blvd W', status: 'open' },
  { id: 'r-sushi', name: 'Sakura Sushi', cuisine: 'Japanese', address: '2020 Guy St', status: 'paused' },
  { id: 'r-taco', name: 'Taco Verde', cuisine: 'Mexican', address: '1600 Ste-Catherine St W', status: 'closed' },
];

const menuItems: MenuItem[] = [
  { id: 'm-1', restaurantId: 'r-green', name: 'Sourdough loaf', description: 'Baked this morning, crusty and tangy.', originalPrice: 8, price: 3.5, status: 'available' },
  { id: 'm-2', restaurantId: 'r-green', name: 'Croissant box (4)', description: 'Butter croissants from the morning batch.', originalPrice: 12, price: 5, status: 'available' },
  { id: 'm-3', restaurantId: 'r-green', name: 'Veggie quiche slice', description: 'Spinach, feta and roasted peppers.', originalPrice: 7, price: 3, status: 'available' },
  { id: 'm-4', restaurantId: 'r-green', name: 'Cinnamon rolls (2)', description: 'Glazed, still soft.', originalPrice: 6, price: 2.5, status: 'sold_out' },
  { id: 'm-5', restaurantId: 'r-sushi', name: 'Salmon maki (8 pcs)', description: 'Fresh from the lunch service.', originalPrice: 11, price: 5.5, status: 'available' },
  { id: 'm-6', restaurantId: 'r-sushi', name: 'Veggie bento', description: 'Rice, tofu, pickles and greens.', originalPrice: 14, price: 6, status: 'available' },
  { id: 'm-7', restaurantId: 'r-sushi', name: 'Miso soup', description: 'Warm and comforting.', originalPrice: 4, price: 1.5, status: 'sold_out' },
  { id: 'm-8', restaurantId: 'r-taco', name: 'Taco trio', description: 'Chicken, bean and veggie tacos.', originalPrice: 12, price: 5, status: 'sold_out' },
  { id: 'm-9', restaurantId: 'r-taco', name: 'Burrito bowl', description: 'Rice, black beans, corn, salsa.', originalPrice: 13, price: 6, status: 'sold_out' },
];

const orders: Order[] = [
  {
    id: '1001', customerId: 'u-alice', customerName: 'Alice Martin', restaurantId: 'r-green', restaurantName: 'Green Leaf Bakery',
    lines: [{ itemId: 'm-1', name: 'Sourdough loaf', price: 3.5, quantity: 1 }],
    specialInstructions: '', status: 'ready', total: 3.5, createdAt: minutesAgo(55),
  },
  {
    id: '1002', customerId: 'u-bob', customerName: 'Bob Nguyen', restaurantId: 'r-green', restaurantName: 'Green Leaf Bakery',
    lines: [{ itemId: 'm-2', name: 'Croissant box (4)', price: 5, quantity: 2 }],
    specialInstructions: 'Please pack separately.', status: 'preparing', total: 10, createdAt: minutesAgo(25),
  },
  {
    id: '1003', customerId: 'u-alice', customerName: 'Alice Martin', restaurantId: 'r-green', restaurantName: 'Green Leaf Bakery',
    lines: [
      { itemId: 'm-3', name: 'Veggie quiche slice', price: 3, quantity: 2 },
      { itemId: 'm-1', name: 'Sourdough loaf', price: 3.5, quantity: 1 },
    ],
    specialInstructions: 'No rush, I will pick up at 6.', status: 'placed', total: 9.5, createdAt: minutesAgo(8),
  },
  {
    id: '1004', customerId: 'u-bob', customerName: 'Bob Nguyen', restaurantId: 'r-green', restaurantName: 'Green Leaf Bakery',
    lines: [{ itemId: 'm-3', name: 'Veggie quiche slice', price: 3, quantity: 1 }],
    specialInstructions: '', status: 'placed', total: 3, createdAt: minutesAgo(3),
  },
];

/** The in-memory "database" the *Api stubs read and write. */
export const db = { users, restaurants, menuItems, orders };
