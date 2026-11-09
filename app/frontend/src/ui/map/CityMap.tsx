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

/** R-DASH-03: six queued orders is a long queue (red), three to five is building (amber). */
export const LONG_QUEUE = 6;
const BUILDING_QUEUE = 3;

/** A paused restaurant is red too: it is not taking orders. A closed one keeps its greyed look (no band). */
export function queueBand(r: { status: Restaurant['status']; queued?: number }): MapPin['band'] {
  if (r.status === 'closed' || r.queued === undefined) return undefined;
  if (r.status === 'paused' || r.queued >= LONG_QUEUE) return 'long';
  return r.queued >= BUILDING_QUEUE ? 'building' : 'short';
}

const COURIER_STATE_LABEL = { offline: 'Offline', idle: 'Online, free', assigned: 'Heading to a restaurant', carrying: 'Delivering' } as const;
const ON_DUTY = ['idle', 'assigned', 'carrying'] as const;

/**
 * The city at a glance (R-ORD-01): restaurants, couriers, and customer addresses.
 * With the city read's counts, restaurant pins are coloured by queue length (R-DASH-03) and labelled "Name · N queued"
 * (`showLabels={false}` hides the labels; the switch is #273's). Without counts they are coloured by status.
 * Hover a pin for details; pass `onSelectRestaurant` to make restaurant pins clickable.
 */
export default function CityMap({
  restaurants,
  couriers = [],
  homes = [],
  onSelectRestaurant,
  showLabels = true,
  height = 340,
}: {
  /** The menu counts are optional: the city read doesn't carry them. `queued` comes from the city read (#268). */
  restaurants: (Restaurant & Partial<RestaurantSummary> & { queued?: number })[];
  couriers?: CourierLocation[];
  homes?: HomePin[];
  onSelectRestaurant?: (restaurantId: string) => void;
  showLabels?: boolean;
  height?: number;
}) {
  const onDuty = couriers.filter((c) => c.courierState !== 'offline');
  // A restaurant without coordinates (new sign-up, not geocoded yet) has no pin, so it can't stretch the bounds.
  const unplaced = restaurants.filter((r) => !point(r.lat, r.lng));
  const byQueue = restaurants.some((r) => r.queued !== undefined);

  const pins: MapPin[] = [
    ...restaurants.filter((r) => point(r.lat, r.lng)).map((r) => ({
      id: `r-${r.id}`,
      kind: 'restaurant' as const,
      at: point(r.lat, r.lng)!,
      label: r.name,
      hue: restaurantHue(r),
      status: r.status,
      band: queueBand(r),
      tag: showLabels && r.queued !== undefined ? `${r.name} · ${r.queued} queued` : undefined,
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
    ...onDuty.map((c) => ({
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
        {byQueue ? (
          <>
            <span>
              <i className="lg-band lg-short" /> Short queue
            </span>
            <span>
              <i className="lg-band lg-building" /> {BUILDING_QUEUE}–{LONG_QUEUE - 1} queued
            </span>
            <span>
              <i className="lg-band lg-long" /> {LONG_QUEUE}+ queued or paused
            </span>
            <span>
              <i className="lg-off lg-closed" /> Closed
            </span>
          </>
        ) : (
          <>
            <span>
              <i className="lg-open" /> Open
            </span>
            <span>
              <i className="lg-off" /> Paused / closed
            </span>
          </>
        )}
        {onDuty.length > 0 &&
          ON_DUTY.map((state) => (
            <span key={state}>
              <i className={`lg-rider lg-${state}`} /> {COURIER_STATE_LABEL[state]}
            </span>
          ))}
        {homes.length > 0 && (
          <span>
            <i className="lg-home" /> {homes.length > 1 ? 'Customers' : 'You'}
          </span>
        )}
      </div>
      {unplaced.length > 0 && <p className="map-unplaced">Not on the map: {unplaced.map((r) => r.name).join(', ')}</p>}
    </section>
  );
}
