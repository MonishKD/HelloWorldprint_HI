import { useEffect, useState } from 'react';

export type LatLng = [number, number];

/** Demo only: once picked up, a delivery takes this long to cross the map. */
export const SIM_DELIVERY_MS = 3 * 60_000;
const BIKE_KMH = 16;
/** Streets are longer than a straight line; used when we only know the endpoints. */
const DETOUR = 1.35;

export function km([lat1, lng1]: LatLng, [lat2, lng2]: LatLng): number {
  const rad = Math.PI / 180;
  const dLat = (lat2 - lat1) * rad;
  const dLng = (lng2 - lng1) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLng / 2) ** 2;
  return 12742 * Math.asin(Math.sqrt(h));
}

/** Point at fraction t (0..1) of the path's length. */
export function pointAlong(path: LatLng[], t: number): LatLng {
  if (path.length < 2 || t <= 0) return path[0];
  const legs = path.slice(1).map((p, i) => km(path[i], p));
  let remaining = legs.reduce((a, b) => a + b, 0) * Math.min(t, 1);
  for (let i = 0; i < legs.length; i++) {
    if (remaining <= legs[i] || i === legs.length - 1) {
      const f = legs[i] ? Math.min(remaining / legs[i], 1) : 1;
      const [a, b] = [path[i], path[i + 1]];
      return [a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f];
    }
    remaining -= legs[i];
  }
  return path[path.length - 1];
}

export function deliveryProgress(pickedUpAt?: string, now = Date.now()): number {
  if (!pickedUpAt) return 0;
  return Math.min(1, Math.max(0, (now - new Date(pickedUpAt).getTime()) / SIM_DELIVERY_MS));
}

export function etaMinutes(from: LatLng, to: LatLng, progress: number): number {
  return Math.max(1, Math.round(((km(from, to) * DETOUR) / BIKE_KMH) * 60 * (1 - progress)));
}

/** Re-renders the caller every `ms` while enabled (for live ETAs). */
export function useNow(ms: number | null) {
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    if (ms === null) return;
    const id = setInterval(() => setNow(Date.now()), ms);
    return () => clearInterval(id);
  }, [ms]);
  return now;
}

const routeCache = new Map<string, Promise<LatLng[]>>();

/**
 * Street route from the public OSRM demo server (OpenStreetMap data).
 * Falls back to a straight line when it's unreachable.
 * ponytail: demo server, fine for a prototype; use our own OSRM/Valhalla instance in production.
 */
export function fetchRoute(from: LatLng, to: LatLng): Promise<LatLng[]> {
  const key = `${from}|${to}`;
  if (!routeCache.has(key)) {
    const url = `https://router.project-osrm.org/route/v1/driving/${from[1]},${from[0]};${to[1]},${to[0]}?overview=full&geometries=geojson`;
    routeCache.set(
      key,
      fetch(url, { signal: AbortSignal.timeout(6000) })
        .then((r) => r.json())
        .then((json) => (json.routes[0].geometry.coordinates as [number, number][]).map(([lng, lat]) => [lat, lng] as LatLng))
        .catch(() => [from, to]),
    );
  }
  return routeCache.get(key)!;
}
