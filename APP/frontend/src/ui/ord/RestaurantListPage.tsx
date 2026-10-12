import { Link } from 'react-router-dom';
import { LoadError, StatusBadge, useLoad } from '../common';
import { listRestaurants } from './ordApi';

export default function RestaurantListPage() {
  const { data: restaurants, error, reload } = useLoad(listRestaurants, []);

  return (
    <section>
      <div className="section-head">
        <div>
          <h1>Rescue a meal nearby</h1>
          <p className="muted">Surplus food from local restaurants, at a fraction of the price.</p>
        </div>
      </div>

      {error ? (
        <LoadError what="restaurants" message={error} onRetry={reload} />
      ) : !restaurants ? (
        <p className="muted">Loading restaurants…</p>
      ) : restaurants.length === 0 ? (
        <p className="empty">No restaurants yet. Check back soon!</p>
      ) : (
        <div className="grid">
          {restaurants.map((r) => (
            <Link key={r.id} to={`r/${r.id}`} className={`card restaurant-card ${r.status === 'open' ? '' : 'unavailable'}`}>
              <div className="row">
                <h2>{r.name}</h2>
                <StatusBadge status={r.status} />
              </div>
              <span className="muted">{r.cuisine}</span>
              <span>
                {r.availableCount > 0 ? `${r.availableCount} of ${r.itemCount} items available` : 'Everything is sold out'}
              </span>
            </Link>
          ))}
        </div>
      )}
    </section>
  );
}
