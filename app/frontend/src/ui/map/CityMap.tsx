import type { CourierLocation, Restaurant } from '../../domain';
import type { RestaurantSummary } from '../ord/ordApi';
import { restaurantHue } from '../common';
import { point } from './geo';
import MapView, { type MapPin } from './MapView';

/** Customer address to show (a home pin), e.g. "You" or "#1003 Alice Martin". */
export interface HomePin {
  id: string;
  lat: number;
  lng: number;
  label: string;
}

const COURIER_STATE_LABEL = { offline: 'Offline', idle: 'Online, free', assigned: 'Heading to a restaurant', carrying: 'Delivering' } as const;

/**
 * The city at a glance (R-ORD-01): restaurants coloured by status, couriers, and customer addresses.
 * Hover a pin for details; pass `onSelectRestaurant` to make restaurant pins clickable.
 */
export default function CityMap({
  restaurants,
  couriers = [],
  homes = [],
  onSelectRestaurant,
  height = 340,
}: {
  /** The menu counts are optional: the city read (#268) doesn't carry them. */
  restaurants: (Restaurant & Partial<RestaurantSummary>)[];
  couriers?: CourierLocation[];
  homes?: HomePin[];
  onSelectRestaurant?: (restaurantId: string) => void;
  height?: number;
}) {
  const pins: MapPin[] = [
    // A restaurant without coordinates (new sign-up, not geocoded yet) simply has no pin.
    ...restaurants.filter((r) => point(r.lat, r.lng)).map((r) => ({
      id: `r-${r.id}`,
      kind: 'restaurant' as const,
      at: point(r.lat, r.lng)!,
      label: r.name,
      hue: restaurantHue(r),
      status: r.status,
      detail:
        r.status === 'open'
          ? r.itemCount === undefined
            ? 'Open'
            : `${r.availableCount} of ${r.itemCount} items available${r.bestDiscount ? ` · up to −${r.bestDiscount}%` : ''}`
          : r.status === 'paused'
            ? 'Paused: not taking new orders'
            : 'Closed',
      onSelect: onSelectRestaurant && (() => onSelectRestaurant(r.id)),
    })),
    ...couriers.map((c) => ({
      id: `c-${c.id}`,
      kind: 'rider' as const,
      at: [c.lat, c.lng] as [number, number],
      label: c.name,
      hue: 168,
      status: c.courierState,
      detail: COURIER_STATE_LABEL[c.courierState],
    })),
    ...homes.map((h) => ({ id: `h-${h.id}`, kind: 'home' as const, at: [h.lat, h.lng] as [number, number], label: h.label, hue: 168 })),
  ];

  return (
    <section className="map-card" aria-label="City map">
      <MapView deliveries={[]} pins={pins} height={height} />
      <div className="map-legend">
        <span>
          <i className="lg-open" /> Open
        </span>
        <span>
          <i className="lg-off" /> Paused / closed
        </span>
        {couriers.length > 0 && (
          <span>
            <i className="lg-rider" /> Couriers
          </span>
        )}
        {homes.length > 0 && (
          <span>
            <i className="lg-home" /> {homes.length > 1 ? 'Customers' : 'You'}
          </span>
        )}
      </div>
    </section>
  );
}
