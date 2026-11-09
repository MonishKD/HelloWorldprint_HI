import { useState, type ComponentProps } from 'react';
import { useNavigate } from 'react-router-dom';
import type { CityRestaurant, RestaurantStatus } from '../../domain';
import { useUser } from '../../session';
import { Segmented, StatusBadge, useLoad, usePolling } from '../common';
import { errorMessage, getPreparationQueue, send } from '../dashboard/dashboardApi';
import { setRestaurantStatus } from '../ord/ordApi';
import CityMap from './CityMap';

/** The city map, opening a restaurant's details when its marker is clicked. */
export function CityMapWithDetails({ restaurants, ...map }: Omit<ComponentProps<typeof CityMap>, 'restaurants' | 'onSelectRestaurant'> & { restaurants: CityRestaurant[] }) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  // Looked up on every render, so the counts follow the map's refresh.
  const selected = restaurants.find((r) => r.id === selectedId);
  return (
    <>
      <CityMap {...map} restaurants={restaurants} onSelectRestaurant={setSelectedId} />
      {selected && <RestaurantDetailsPanel key={selected.id} restaurant={selected} onClose={() => setSelectedId(null)} />}
    </>
  );
}

function RestaurantDetailsPanel({ restaurant: r, onClose }: { restaurant: CityRestaurant; onClose: () => void }) {
  const user = useUser();
  const navigate = useNavigate();
  const [error, setError] = useState('');
  const ownRestaurant = user.role === 'restaurant' && user.restaurantId === r.id;

  async function changeStatus(status: RestaurantStatus) {
    setError('');
    try {
      await send(`Set ${r.name} to ${status}`, () => setRestaurantStatus(r.id, status));
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  return (
    <section className="card restaurant-details" aria-label={`${r.name} details`}>
      <header className="restaurant-details-head">
        <h3>{r.name}</h3>
        <StatusBadge status={r.status} />
        <button type="button" className="btn btn-ghost btn-sm" onClick={onClose}>
          Close
        </button>
      </header>

      <p className="muted">
        {r.queued} queued · {r.inPreparation} in preparation · {r.ready} ready
      </p>

      <p>
        <strong>Sold out:</strong> {r.soldOutItems.length === 0 ? <span className="muted">nothing</span> : r.soldOutItems.join(', ')}
      </p>

      {(user.role === 'operator' || ownRestaurant) && <RestaurantQueue restaurantId={r.id} />}

      {error && (
        <p className="alert alert-error" role="alert">
          {error}
        </p>
      )}

      {user.role === 'operator' && (
        <Segmented<RestaurantStatus>
          label={`${r.name} status`}
          value={r.status}
          options={['open', 'paused']}
          onChange={changeStatus}
          confirmText={(s) => `Set ${r.name} to ${s}?`}
        />
      )}
      {user.role === 'customer' && (
        <button type="button" className="btn btn-sm" onClick={() => navigate(`/customer/r/${r.id}`)}>
          Open the menu
        </button>
      )}
      {ownRestaurant && (
        <button type="button" className="btn btn-sm" onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}>
          Go to my orders
        </button>
      )}
    </section>
  );
}

/** The order queue is for the operator and the restaurant's own staff only (Appendix A). */
function RestaurantQueue({ restaurantId }: { restaurantId: string }) {
  const { data, error, reload } = useLoad(() => getPreparationQueue(restaurantId), [restaurantId]);
  usePolling(reload);

  if (error && !data) return <p className="muted">Couldn't load the order queue: {error}</p>;
  if (!data) return <p className="muted">Loading the order queue…</p>;
  if (data.length === 0) return <p className="muted">No orders in the queue.</p>;
  return (
    <ol className="restaurant-queue">
      {data.map((slot) => (
        <li key={slot.orderId}>
          #{slot.orderId} <StatusBadge status={slot.status} />
        </li>
      ))}
    </ol>
  );
}
