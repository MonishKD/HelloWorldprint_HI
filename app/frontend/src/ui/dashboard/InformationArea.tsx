import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { useUser } from '../../session';
import { Switch, useLoad, usePolling } from '../common';
import { getCityView, onCommandFinished } from './dashboardApi';

interface DashboardInfo {
  selectedRestaurantId: string | null;
  selectRestaurant: (restaurantId: string | null) => void;
  showLabels: boolean;
  setShowLabels: (show: boolean) => void;
}

const DashboardInfoContext = createContext<DashboardInfo | null>(null);

/** What the map and the information area share: the selected restaurant and the labels switch. */
export function DashboardInfoProvider({ children }: { children: ReactNode }) {
  const [selectedRestaurantId, selectRestaurant] = useState<string | null>(null);
  const [showLabels, setShowLabels] = useState(true);
  return <DashboardInfoContext.Provider value={{ selectedRestaurantId, selectRestaurant, showLabels, setShowLabels }}>{children}</DashboardInfoContext.Provider>;
}

export function useDashboardInfo(): DashboardInfo {
  const info = useContext(DashboardInfoContext);
  if (!info) throw new Error('useDashboardInfo must be used inside DashboardLayout');
  return info;
}

/** Who is signed in, the selected restaurant's counts (from the city read) and the map labels switch. */
export default function InformationArea() {
  const user = useUser();
  const { selectedRestaurantId, showLabels, setShowLabels } = useDashboardInfo();
  const { data, reload } = useLoad(getCityView, []);
  useEffect(() => onCommandFinished(reload), [reload]);
  usePolling(reload);
  const selected = data?.restaurants.find((r) => r.id === selectedRestaurantId);

  return (
    <section className="info-area" aria-label="Information">
      <span>
        <strong>{user.name}</strong> <small className="muted info-role">{user.role}</small>
      </span>
      <span className="info-selected">
        {selected ? (
          <>
            <strong>{selected.name}</strong>: {selected.queued} queued · {selected.inPreparation} in preparation · {selected.ready} ready
          </>
        ) : (
          <span className="muted">Select a restaurant on the map to see its counts</span>
        )}
      </span>
      <span className="info-labels">
        Map labels <Switch checked={showLabels} label="Map labels" onChange={() => setShowLabels(!showLabels)} />
      </span>
    </section>
  );
}
