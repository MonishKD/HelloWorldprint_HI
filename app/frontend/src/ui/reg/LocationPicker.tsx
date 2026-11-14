import MapView from '../map/MapView';

/** Where a new account is (#244): a click on the city map, or the coordinates typed in. Kept as text so a field can be cleared. */
export interface PickedLocation {
  lat: string;
  lng: string;
}

/**
 * Location picker for the registration form. Only the tiles show (no markers before login), plus the picked point.
 * The latitude and longitude fields are the fallback, and show what a click picked.
 */
export default function LocationPicker({ value, onChange, label }: { value: PickedLocation; onChange: (next: PickedLocation) => void; label: string }) {
  const lat = Number(value.lat);
  const lng = Number(value.lng);
  const picked = value.lat !== '' && value.lng !== '' && Number.isFinite(lat) && Number.isFinite(lng);

  return (
    <fieldset className="location-picker">
      <legend className="field-label">{label}</legend>
      <p className="muted small">Click the map, or type the coordinates.</p>
      <MapView
        deliveries={[]}
        pins={picked ? [{ id: 'picked', kind: 'home', at: [lat, lng], label: 'Your location', hue: 168 }] : []}
        height={220}
        onPick={([la, ln]) => onChange({ lat: la.toFixed(5), lng: ln.toFixed(5) })}
      />
      <div className="location-fields">
        <label className="field">
          <span>Latitude</span>
          <input type="number" required step="any" min={-90} max={90} value={value.lat} onChange={(e) => onChange({ ...value, lat: e.target.value })} />
        </label>
        <label className="field">
          <span>Longitude</span>
          <input type="number" required step="any" min={-180} max={180} value={value.lng} onChange={(e) => onChange({ ...value, lng: e.target.value })} />
        </label>
      </div>
    </fieldset>
  );
}
