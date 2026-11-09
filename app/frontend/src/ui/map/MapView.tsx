import * as maplibregl from 'maplibre-gl';
import type { Feature, FeatureCollection } from 'geojson';
import type { GeoJSONSource } from 'maplibre-gl';
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?url';
import 'maplibre-gl/dist/maplibre-gl.css';
import { useEffect, useRef, useState } from 'react';
import { initials } from '../common';
import { iconSvg } from '../icons';
import { deliveryProgress, fetchRoute, pointAlong, type LatLng } from './geo';
import { buildStyle, routeColors } from './mapStyle';

// MapLibre 6 loads its worker from a separate file; Vite has to be told where it is, or the map shows pins but no tiles.
maplibregl.setWorkerUrl(workerUrl);

export interface MapDelivery {
  id: string;
  from: LatLng;
  fromLabel: string;
  hue: number;
  to: LatLng;
  toLabel: string;
  /** Shows a courier marker: parked at the restaurant until pickedUpAt, then moving along the route. */
  courierName?: string;
  pickedUpAt?: string;
}

/** A standalone point on the map (city map, pickup requests). */
export interface MapPin {
  id: string;
  at: LatLng;
  label: string;
  hue: number;
  /** restaurant: store pin coloured by `status`; rider: a courier at rest; home: a customer address; request: dashed store pin. */
  kind?: 'restaurant' | 'rider' | 'home' | 'request';
  /** restaurant status (open/paused/closed) or courier state (idle/assigned/carrying/offline); drives the pin's look. */
  status?: string;
  /** Second line of the hover label. */
  detail?: string;
  /** Makes the pin clickable (and keyboard-focusable). */
  onSelect?: () => void;
  /** Restaurant queue band (R-DASH-03): colours the pin instead of `status`. */
  band?: 'short' | 'building' | 'long';
  /** Always-visible label under the pin, e.g. "Uniburger · 4 queued". */
  tag?: string;
}

const DOWNTOWN: LatLng = [45.5017, -73.5673];
// Extra bottom padding keeps pins clear of the attribution tag.
const FIT = { padding: { top: 44, right: 44, bottom: 60, left: 44 }, maxZoom: 15, duration: 0 };

const lngLat = ([lat, lng]: LatLng): [number, number] => [lng, lat];

type PinKind = 'store' | 'home' | 'courier' | 'request' | 'restaurant' | 'rider';
const SIZES: Record<PinKind, number> = { store: 34, home: 34, courier: 40, request: 34, restaurant: 38, rider: 30 };

/** DOM marker. Only restaurants, customer homes and couriers ever appear on the map: no other points of interest. */
function marker(
  at: LatLng,
  kind: PinKind,
  inner: string,
  label: string,
  opts: { hue?: number; status?: string; detail?: string; onSelect?: () => void; band?: string; tag?: string } = {},
) {
  const size = SIZES[kind];
  const el = document.createElement('div');
  el.className = `map-marker is-${kind}${opts.status ? ` status-${opts.status}` : ''}${opts.band ? ` band-${opts.band}` : ''}`;
  el.style.cssText = `width:${size}px;height:${size}px;--hue:${opts.hue ?? 168}`;
  el.dataset.label = label;
  if (opts.detail) el.dataset.detail = `\n${opts.detail}`;
  el.innerHTML = inner;
  if (opts.tag) {
    const tag = document.createElement('span');
    tag.className = 'pin-tag';
    tag.textContent = opts.tag;
    el.append(tag);
  }
  if (opts.onSelect) {
    const select = opts.onSelect;
    el.classList.add('is-clickable');
    el.tabIndex = 0;
    el.setAttribute('role', 'link');
    el.setAttribute('aria-label', label);
    el.addEventListener('click', select);
    el.addEventListener('keydown', (e) => e.key === 'Enter' && select());
  }
  return new maplibregl.Marker({ element: el }).setLngLat(lngLat(at));
}

const storePin = (name: string) => {
  const span = document.createElement('span');
  span.className = 'pin-store';
  span.textContent = initials(name);
  return span.outerHTML;
};
const homePin = `<span class="pin-home">${iconSvg('home')}</span>`;
const courierPin = `<span class="pin-courier">${iconSvg('bike', 18)}</span>`;
const riderPin = `<span class="pin-rider">${iconSvg('bike', 15)}</span>`;

function courierAt(d: MapDelivery, path: LatLng[]): LatLng {
  return d.pickedUpAt ? pointAlong(path, deliveryProgress(d.pickedUpAt)) : d.from;
}

/** Stylised OpenStreetMap view: delivery routes with a moving courier, and/or standalone pins. `onPick` makes a click choose a point. */
export default function MapView({
  deliveries,
  pins = [],
  height = 280,
  onPick,
}: {
  deliveries: MapDelivery[];
  pins?: MapPin[];
  height?: number;
  onPick?: (at: LatLng) => void;
}) {
  const el = useRef<HTMLDivElement>(null);
  const map = useRef<maplibregl.Map | null>(null);
  const markers = useRef<maplibregl.Marker[]>([]);
  const couriers = useRef(new Map<string, maplibregl.Marker>());
  const lines = useRef<FeatureCollection>({ type: 'FeatureCollection', features: [] });
  const fittedFor = useRef('');
  const fittedBounds = useRef<maplibregl.LngLatBounds | null>(null);
  const [paths, setPaths] = useState<Record<string, LatLng[]>>({});
  // The map is created once, so its click handler reads the latest onPick through a ref.
  const pick = useRef(onPick);
  pick.current = onPick;
  // Parents re-render every second for ETAs; only redraw when the data actually changes.
  const dataKey = JSON.stringify([deliveries, pins]);

  /** (Re)adds the route layers; needed after every style load, e.g. when the theme flips. */
  function syncRoutes() {
    const m = map.current;
    if (!m || !m.isStyleLoaded()) return;
    const { line, casing } = routeColors();
    const source = m.getSource('routes') as GeoJSONSource | undefined;
    if (source) {
      source.setData(lines.current);
      return;
    }
    m.addSource('routes', { type: 'geojson', data: lines.current });
    const layout = { 'line-cap': 'round', 'line-join': 'round' } as const;
    const w = (a: number, b: number) => ['interpolate', ['linear'], ['zoom'], 10, a, 17, b] as never;
    m.addLayer({ id: 'route-casing', type: 'line', source: 'routes', layout, paint: { 'line-color': casing, 'line-width': w(6, 14), 'line-opacity': 0.95 } });
    m.addLayer({ id: 'route-line', type: 'line', source: 'routes', layout, paint: { 'line-color': line, 'line-width': w(3, 7) } });
  }

  useEffect(() => {
    const m = new maplibregl.Map({
      container: el.current!,
      style: buildStyle(),
      center: lngLat(DOWNTOWN),
      zoom: 12,
      attributionControl: { compact: true },
      dragRotate: false,
      pitchWithRotate: false,
      scrollZoom: false,
      fadeDuration: 0,
    });
    m.touchZoomRotate.disableRotation();
    m.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-left');
    map.current = m;

    // Surface tile / style problems in the console instead of failing silently.
    m.on('error', (e) => console.error('[map]', e.error?.message ?? e));
    m.on('style.load', syncRoutes);
    m.on('click', (e) => pick.current?.([e.lngLat.lat, e.lngLat.lng]));
    const themeWatch = new MutationObserver(() => {
      // style.load fires again after setStyle, which re-adds the route in the new colours
      m.setStyle(buildStyle());
    });
    themeWatch.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });

    // Re-measure when the card resizes (entrance animation, layout shifts).
    const resize = new ResizeObserver(() => {
      m.resize();
      if (fittedBounds.current) m.fitBounds(fittedBounds.current, FIT);
    });
    resize.observe(el.current!);

    return () => {
      resize.disconnect();
      themeWatch.disconnect();
      m.remove();
      map.current = null;
      // A remounted map (React StrictMode, hot reload) must frame its pins again.
      fittedFor.current = '';
      fittedBounds.current = null;
    };
  }, []);

  useEffect(() => {
    let alive = true;
    for (const d of deliveries) {
      fetchRoute(d.from, d.to).then((path) => alive && setPaths((prev) => (prev[d.id] === path ? prev : { ...prev, [d.id]: path })));
    }
    return () => {
      alive = false;
    };
  }, [dataKey]);

  useEffect(() => {
    const m = map.current;
    if (!m) return;
    markers.current.forEach((x) => x.remove());
    markers.current = [];
    couriers.current.clear();
    const bounds = new maplibregl.LngLatBounds();
    const features: Feature[] = [];
    const add = (mk: maplibregl.Marker) => {
      mk.addTo(m);
      markers.current.push(mk);
    };

    for (const d of deliveries) {
      const path = paths[d.id] ?? [d.from, d.to];
      features.push({ type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: path.map(lngLat) } });
      add(marker(d.from, 'store', storePin(d.fromLabel), d.fromLabel, { hue: d.hue }));
      add(marker(d.to, 'home', homePin, d.toLabel));
      if (d.courierName) {
        const courier = marker(courierAt(d, path), 'courier', courierPin, d.courierName);
        add(courier);
        couriers.current.set(d.id, courier);
      }
      path.forEach((p) => bounds.extend(lngLat(p)));
    }
    for (const p of pins) {
      const opts = { hue: p.hue, status: p.status, detail: p.detail, onSelect: p.onSelect, band: p.band, tag: p.tag };
      const kind = p.kind ?? 'request';
      add(marker(p.at, kind, kind === 'home' ? homePin : kind === 'rider' ? riderPin : storePin(p.label), p.label, opts));
      bounds.extend(lngLat(p.at));
    }

    lines.current = { type: 'FeatureCollection', features };
    syncRoutes();

    const fitKey = deliveries.map((d) => d.id).concat(pins.map((p) => p.id)).join(',');
    if (!bounds.isEmpty() && fitKey !== fittedFor.current) {
      fittedFor.current = fitKey;
      fittedBounds.current = bounds;
      m.resize();
      m.fitBounds(bounds, FIT);
    }
  }, [dataKey, paths]);

  // Move couriers without redrawing the rest of the map.
  useEffect(() => {
    if (!deliveries.some((d) => d.courierName && d.pickedUpAt)) return;
    const id = setInterval(() => {
      for (const d of deliveries) couriers.current.get(d.id)?.setLngLat(lngLat(courierAt(d, paths[d.id] ?? [d.from, d.to])));
    }, 250);
    return () => clearInterval(id);
  }, [dataKey, paths]);

  return <div ref={el} className={onPick ? 'map is-picker' : 'map'} style={{ height }} role="region" aria-label="Map" />;
}
