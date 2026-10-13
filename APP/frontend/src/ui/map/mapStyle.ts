import type { StyleSpecification } from 'maplibre-gl';

/** Reads a --map-* design token (src/styles/tokens.css) so the map follows the active theme. */
const token = (name: string) => getComputedStyle(document.documentElement).getPropertyValue(name).trim();

export const routeColors = () => ({ line: token('--map-route'), casing: token('--map-route-casing') });

const MAJOR = ['motorway', 'trunk', 'primary'];
const MID = ['secondary', 'tertiary'];
const MINOR = ['minor', 'service'];

/** Line width that grows with zoom: [zoomA, widthA, zoomB, widthB]. */
const grow = ([z1, w1, z2, w2]: [number, number, number, number]) => ['interpolate', ['exponential', 1.5], ['zoom'], z1, w1, z2, w2] as never;

/**
 * Minimal, label-free basemap from OpenFreeMap vector tiles (OpenStreetMap data):
 * land, parks, water, buildings and roads only. No POIs, no text.
 * Colours come from the --map-* tokens, so changing the palette in tokens.css restyles the map.
 */
export function buildStyle(): StyleSpecification {
  const road = (id: string, classes: string[], color: string, widths: [number, number, number, number]) => ({
    id,
    type: 'line' as const,
    source: 'osm',
    'source-layer': 'transportation',
    filter: ['all', ['in', ['get', 'class'], ['literal', classes]], ['!=', ['get', 'brunnel'], 'tunnel']] as never,
    layout: { 'line-cap': 'round' as const, 'line-join': 'round' as const },
    paint: { 'line-color': color, 'line-width': grow(widths) },
  });

  const land = token('--map-land');
  const park = token('--map-park');
  const water = token('--map-water');
  const building = token('--map-building');
  const roadFill = token('--map-road');
  const roadCasing = token('--map-road-casing');
  const majorFill = token('--map-major');
  const majorCasing = token('--map-major-casing');

  return {
    version: 8,
    sources: {
      osm: {
        type: 'vector',
        url: 'https://tiles.openfreemap.org/planet',
        attribution:
          '<a href="https://openfreemap.org" target="_blank">OpenFreeMap</a> <a href="https://www.openmaptiles.org/" target="_blank">&copy; OpenMapTiles</a> Data from <a href="https://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap</a>',
      },
    },
    layers: [
      { id: 'land', type: 'background', paint: { 'background-color': land } },
      { id: 'landcover', type: 'fill', source: 'osm', 'source-layer': 'landcover', filter: ['in', ['get', 'class'], ['literal', ['grass', 'wood', 'farmland']]], paint: { 'fill-color': park, 'fill-opacity': 0.85 } },
      { id: 'park', type: 'fill', source: 'osm', 'source-layer': 'park', paint: { 'fill-color': park } },
      { id: 'building', type: 'fill', source: 'osm', 'source-layer': 'building', minzoom: 13, paint: { 'fill-color': building, 'fill-opacity': ['interpolate', ['linear'], ['zoom'], 13, 0, 15, 1] } },
      { id: 'water', type: 'fill', source: 'osm', 'source-layer': 'water', paint: { 'fill-color': water } },
      { id: 'waterway', type: 'line', source: 'osm', 'source-layer': 'waterway', paint: { 'line-color': water, 'line-width': grow([10, 0.8, 16, 4]) } },
      // casings first so the fills sit on top and read as clean outlines
      road('minor-casing', MINOR, roadCasing, [12, 1.4, 17, 12]),
      road('mid-casing', MID, roadCasing, [10, 1.6, 17, 17]),
      road('major-casing', MAJOR, majorCasing, [8, 1.6, 17, 24]),
      road('minor', MINOR, roadFill, [12, 0.7, 17, 9]),
      road('mid', MID, roadFill, [10, 1, 17, 14]),
      road('major', MAJOR, majorFill, [8, 1, 17, 19]),
    ],
  };
}
