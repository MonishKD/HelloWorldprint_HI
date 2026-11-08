import { useEffect } from 'react';
import { Empty, LoadError, Loading, useLoad, usePolling } from '../common';
import { getCityView, onCommandFinished } from '../dashboard/dashboardApi';
import { CityMapWithDetails } from './RestaurantDetailsPanel';

/** The city read on a map, for every dashboard. Reloads after each command and every few seconds. */
export default function CityMapPanel() {
  const { data, error, reload } = useLoad(getCityView, []);
  useEffect(() => onCommandFinished(reload), [reload]);
  usePolling(reload);

  return (
    <section className="stack">
      <h2 className="section-title">City map</h2>
      {error && !data ? (
        <LoadError what="the city map" message={error} onRetry={reload} />
      ) : !data ? (
        <Loading what="the city map" />
      ) : data.restaurants.length === 0 && data.couriers.length === 0 ? (
        <Empty icon="store" title="The city is empty">
          Restaurants and on-duty couriers will show up here.
        </Empty>
      ) : (
        <CityMapWithDetails restaurants={data.restaurants} couriers={data.couriers} />
      )}
    </section>
  );
}