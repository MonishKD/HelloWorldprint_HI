// rvwApi (ui.rvw): every rating call to the backend (#325, R-RVW-01, R-RVW-02), against the routes from #324.
import type { CityRestaurant, Order, Rating, RatingScale } from '../../domain';
import { toRating } from '../adapters';
import { request } from '../http';

/** GET /ratings/scale: the star range from the backend's config (any signed-in user). */
export async function getRatingScale(): Promise<RatingScale> {
  const s = await request<{ minStars: number; maxStars: number }>('/ratings/scale');
  return { minStars: Number(s.minStars), maxStars: Number(s.maxStars) };
}

/**
 * POST /orders/{id}/rating (customer only). The backend refuses an order that isn't delivered, isn't the customer's,
 * was already rated, or has stars outside the scale.
 */
export async function rateOrder(order: Order, restaurantStars: number, courierStars: number): Promise<Rating> {
  return toRating(await request(`/orders/${order.id}/rating`, { method: 'POST', body: { restaurantStars, courierStars } }))!;
}

/** What the customer gave, sent with GET /dashboard/orders. */
export const ratingOf = (order: Order): Rating | undefined => order.rating;

/** The average and count sent with GET /dashboard/city (the backend rounds to one decimal). */
export const restaurantRating = (r: CityRestaurant): { average?: number; count: number } => ({ average: r.averageRating, count: r.ratingCount });
