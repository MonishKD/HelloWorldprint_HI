// Static seed data for v1 (mock "database"). Everything lives in memory and resets on page reload.
// MOCK ONLY: delete this file when the real backend is connected.
import type { MenuItem, Order, Restaurant, User } from './domain';

const minutesAgo = (n: number) => new Date(Date.now() - n * 60_000).toISOString();

const users: User[] = [
  { id: 'u-alice', name: 'Alice Martin', email: 'alice@demo.com', password: 'demo123', role: 'customer', address: '4100 Rue Saint-Denis, Apt 4', lat: 45.5206, lng: -73.5786 },
  { id: 'u-bob', name: 'Bob Nguyen', email: 'bob@demo.com', password: 'demo123', role: 'customer', address: '1000 Rue Wellington', lat: 45.4935, lng: -73.5575 },
  { id: 'u-green', name: 'Green Leaf Bakery', email: 'greenleaf@demo.com', password: 'demo123', role: 'restaurant', restaurantId: 'r-green' },
  { id: 'u-sushi', name: 'Sakura Sushi', email: 'sakura@demo.com', password: 'demo123', role: 'restaurant', restaurantId: 'r-sushi' },
  { id: 'u-taco', name: 'Taco Verde', email: 'tacoverde@demo.com', password: 'demo123', role: 'restaurant', restaurantId: 'r-taco' },
  { id: 'u-carl', name: 'Carl Courier', email: 'courier@demo.com', password: 'demo123', role: 'courier', courierState: 'offline', lat: 45.4988, lng: -73.5712 },
  { id: 'u-dina', name: 'Dina Dasher', email: 'dina@demo.com', password: 'demo123', role: 'courier', courierState: 'carrying', lat: 45.5105, lng: -73.5648 },
  { id: 'u-eli', name: 'Eli Express', email: 'eli@demo.com', password: 'demo123', role: 'courier', courierState: 'offline', lat: 45.5032, lng: -73.5848 },
  { id: 'u-olga', name: 'Olga Operator', email: 'operator@demo.com', password: 'demo123', role: 'operator' },
];

const restaurants: Restaurant[] = [
  {
    id: 'r-green', name: 'Green Leaf Bakery', cuisine: 'Bakery', address: '1455 De Maisonneuve Blvd W', status: 'open', lat: 45.4972, lng: -73.579,
    hue: 32, rating: 4.8, distanceKm: 0.6, pickupWindow: 'Today 17:00–19:30',
  },
  {
    id: 'r-sushi', name: 'Sakura Sushi', cuisine: 'Japanese', address: '3715 Boul. Saint-Laurent', status: 'paused', lat: 45.5148, lng: -73.5736,
    hue: 350, rating: 4.6, distanceKm: 1.4, pickupWindow: 'Today 20:00–21:00',
  },
  {
    id: 'r-taco', name: 'Taco Verde', cuisine: 'Mexican', address: '400 Rue Saint-Paul E', status: 'closed', lat: 45.5086, lng: -73.5522,
    hue: 95, rating: 4.4, distanceKm: 2.1, pickupWindow: 'Tomorrow 12:00–14:00',
  },
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

const line = (itemId: string, quantity: number) => {
  const m = menuItems.find((i) => i.id === itemId)!;
  return { itemId, name: m.name, price: m.price, originalPrice: m.originalPrice, quantity };
};

const orders: Order[] = [
  {
    id: '1001', customerId: 'u-alice', customerName: 'Alice Martin', deliveryAddress: '4100 Rue Saint-Denis, Apt 4', deliveryLat: 45.5206, deliveryLng: -73.5786,
    restaurantId: 'r-green', restaurantName: 'Green Leaf Bakery', lines: [line('m-1', 1)],
    specialInstructions: '', status: 'delivered', total: 3.5, createdAt: minutesAgo(180), courierId: 'u-carl', courierName: 'Carl Courier',
  },
  {
    id: '1002', customerId: 'u-bob', customerName: 'Bob Nguyen', deliveryAddress: '1000 Rue Wellington', deliveryLat: 45.4935, deliveryLng: -73.5575,
    restaurantId: 'r-green', restaurantName: 'Green Leaf Bakery', lines: [line('m-2', 2)],
    specialInstructions: 'Please pack separately.', status: 'preparing', total: 10, createdAt: minutesAgo(25),
  },
  {
    id: '1003', customerId: 'u-alice', customerName: 'Alice Martin', deliveryAddress: '4100 Rue Saint-Denis, Apt 4', deliveryLat: 45.5206, deliveryLng: -73.5786,
    restaurantId: 'r-green', restaurantName: 'Green Leaf Bakery', lines: [line('m-3', 2), line('m-1', 1)],
    specialInstructions: 'Buzzer 4, please call when downstairs.', status: 'placed', total: 9.5, createdAt: minutesAgo(8),
  },
  {
    id: '1004', customerId: 'u-bob', customerName: 'Bob Nguyen', deliveryAddress: '1000 Rue Wellington', deliveryLat: 45.4935, deliveryLng: -73.5575,
    restaurantId: 'r-green', restaurantName: 'Green Leaf Bakery', lines: [line('m-3', 1)],
    specialInstructions: '', status: 'placed', total: 3, createdAt: minutesAgo(3),
  },
  {
    id: '1005', customerId: 'u-alice', customerName: 'Alice Martin', deliveryAddress: '4100 Rue Saint-Denis, Apt 4', deliveryLat: 45.5206, deliveryLng: -73.5786,
    restaurantId: 'r-green', restaurantName: 'Green Leaf Bakery', lines: [line('m-2', 1)],
    specialInstructions: '', status: 'ready', total: 5, createdAt: minutesAgo(40),
  },
  {
    id: '1006', customerId: 'u-bob', customerName: 'Bob Nguyen', deliveryAddress: '1000 Rue Wellington', deliveryLat: 45.4935, deliveryLng: -73.5575,
    restaurantId: 'r-sushi', restaurantName: 'Sakura Sushi', lines: [line('m-6', 1), line('m-5', 1)],
    specialInstructions: '', status: 'picked_up', total: 11.5, createdAt: minutesAgo(50), courierId: 'u-dina', courierName: 'Dina Dasher', pickedUpAt: minutesAgo(1),
  },
  {
    id: '1007', customerId: 'u-alice', customerName: 'Alice Martin', deliveryAddress: '4100 Rue Saint-Denis, Apt 4', deliveryLat: 45.5206, deliveryLng: -73.5786,
    restaurantId: 'r-sushi', restaurantName: 'Sakura Sushi', lines: [line('m-7', 2)],
    specialInstructions: '', status: 'rejected', rejectReason: 'Soup ran out before we could pack it.', total: 3, createdAt: minutesAgo(300),
  },
];

/** The in-memory "database" the *Api stubs read and write. */
export const db = { users, restaurants, menuItems, orders };
