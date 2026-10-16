import { useState, type CSSProperties } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useUser } from '../../session';
import { Empty, LoadError, Loading, money, restaurantHue, stagger, useLoad } from '../common';
import { Icon } from '../icons';
import CityMap from '../map/CityMap';
import { getCustomerOrderStatuses } from '../dashboard/dashboardApi';
import { listCourierLocations, listRestaurants } from './ordApi';

// Rough per-meal estimate used by food-waste apps; display only.
const KG_CO2E_PER_MEAL = 2.5;

function greeting() {
  const h = new Date().getHours();
  return h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening';
}

export default function RestaurantListPage() {
  const user = useUser();
  const navigate = useNavigate();
  const { data, error, reload } = useLoad(() => Promise.all([listRestaurants(), getCustomerOrderStatuses(user.id), listCourierLocations()]), [user.id]);
  const [query, setQuery] = useState('');
  const [cuisine, setCuisine] = useState('All');
  const [openOnly, setOpenOnly] = useState(false);

  if (error) return <LoadError what="restaurants" message={error} onRetry={reload} />;
  if (!data) return <Loading what="restaurants" />;

  const [restaurants, orders, couriers] = data;
  const rescued = orders.filter((o) => !['rejected', 'cancelled', 'refunded'].includes(o.status)).flatMap((o) => o.lines);
  const meals = rescued.reduce((n, l) => n + l.quantity, 0);
  const saved = rescued.reduce((n, l) => n + (l.originalPrice - l.price) * l.quantity, 0);

  const cuisines = ['All', ...new Set(restaurants.map((r) => r.cuisine).filter(Boolean))]; // cuisine can be empty
  const q = query.trim().toLowerCase();
  const shown = restaurants
    .filter((r) => cuisine === 'All' || r.cuisine === cuisine)
    .filter((r) => !openOnly || r.status === 'open')
    .filter((r) => !q || `${r.name} ${r.cuisine}`.toLowerCase().includes(q))
    .sort((a, b) => Number(b.status === 'open') - Number(a.status === 'open'));

  return (
    <>
      <section className="hero rise">
        <div>
          <span className="eyebrow">{greeting()}, {user.name.split(' ')[0]}</span>
          <h1>
            What will you <em>rescue</em> today?
          </h1>
          {user.address && <p className="muted">Delivering to {user.address}</p>}
        </div>
        <dl className="impact">
          <div>
            <dt>Meals rescued</dt>
            <dd>{meals}</dd>
          </div>
          <div>
            <dt>Money saved</dt>
            <dd>{money(saved)}</dd>
          </div>
          <div>
            <dt>CO₂e avoided</dt>
            <dd>{(meals * KG_CO2E_PER_MEAL).toFixed(1)} kg</dd>
          </div>
        </dl>
      </section>

      <div className="toolbar">
        <label className="search">
          <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
            <path fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" d="m21 21-4.3-4.3M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" />
          </svg>
          <input type="search" placeholder="Search restaurants or cuisines" aria-label="Search restaurants" value={query} onChange={(e) => setQuery(e.target.value)} />
        </label>
        <div className="chips" role="group" aria-label="Filter by cuisine">
          {cuisines.map((c) => (
            <button key={c} type="button" className="chip" aria-pressed={cuisine === c} onClick={() => setCuisine(c)}>
              {c}
            </button>
          ))}
          <button type="button" className="chip" aria-pressed={openOnly} onClick={() => setOpenOnly(!openOnly)}>
            Open now
          </button>
        </div>
      </div>

      <CityMap
        restaurants={shown}
        couriers={couriers.filter((c) => c.courierState !== 'offline')}
        homes={user.lat !== undefined && user.lng !== undefined ? [{ id: user.id, lat: user.lat, lng: user.lng, label: 'You' }] : []}
        onSelectRestaurant={(id) => navigate(`/customer/r/${id}`)}
      />

      {shown.length === 0 ? (
        restaurants.length === 0 ? (
          <Empty icon="store" title="No restaurants yet">
            Restaurants will show up here as soon as they join FortunateGoods.
          </Empty>
        ) : (
          <Empty icon="search" title="No restaurants match">
            Try another search or clear the filters.
          </Empty>
        )
      ) : (
        <div className="r-grid">
          {shown.map((r, i) => (
            <Link
              key={r.id}
              to={`r/${r.id}`}
              className={`r-card rise ${r.status === 'open' ? '' : 'is-off'}`}
              style={{ ...stagger(i), '--hue': restaurantHue(r) } as CSSProperties}
            >
              <div className="r-cover">
                <span className="r-letter" aria-hidden="true">
                  {r.name[0]}
                </span>
                {r.bestDiscount > 0 && (
                  <span className="r-deal">
                    <small>up to</small>−{r.bestDiscount}%
                  </span>
                )}
                {r.status !== 'open' ? (
                  <span className="r-flag">{r.status === 'paused' ? 'Paused' : 'Closed'}</span>
                ) : r.availableCount > 0 ? (
                  <span className="r-left">{r.availableCount} left</span>
                ) : (
                  <span className="r-flag">Sold out</span>
                )}
              </div>
              <div className="r-body">
                <div className="r-row">
                  <h3>{r.name}</h3>
                  {r.rating ? <span className="rating">
                      <Icon name="star" size={12} filled /> {r.rating.toFixed(1)}
                    </span> : <span className="rating">New</span>}
                </div>
                <span className="muted">
                  {r.cuisine || 'New on FortunateGoods'}
                  {r.distanceKm !== undefined && ` · ${r.distanceKm} km`}
                </span>
                {r.pickupWindow && (
                  <span className="pickup">
                    <Icon name="clock" size={14} /> {r.pickupWindow}
                  </span>
                )}
              </div>
            </Link>
          ))}
        </div>
      )}
    </>
  );
}
