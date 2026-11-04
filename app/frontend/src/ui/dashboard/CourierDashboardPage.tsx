import { useState } from 'react';
import { ConfirmChange, Empty, LoadError, Loading, StatusBadge, Switch, useLoad } from '../common';
import { getCourierState, setOnDuty } from '../dlv/dlvApi';
import { errorMessage, logError, send } from './dashboardApi';
import { DashboardLayout } from './DashboardRouter';

export default function CourierDashboardPage() {
  return (
    <DashboardLayout>
      <CourierDashboard />
    </DashboardLayout>
  );
}

function CourierDashboard() {
  const { data: state, error, reload } = useLoad(getCourierState, []);
  const [actionError, setActionError] = useState('');

  async function changeDuty(onDuty: boolean) {
    setActionError('');
    try {
      await send(onDuty ? 'Go on duty' : 'Go off duty', () => setOnDuty(onDuty));
    } catch (e) {
      setActionError(errorMessage(e));
    }
    reload();
  }

  if (error) return <LoadError what="your courier status" message={error} onRetry={reload} />;
  if (!state) return <Loading what="your courier status" />;

  const onDuty = state !== 'offline';

  return (
    <>
      <section className={`courier-hero rise ${onDuty ? 'is-online' : ''}`}>
        <div>
          <span className="eyebrow">Courier</span>
          <h1>{onDuty ? "You're on duty" : "You're off duty"}</h1>
          <p>
            <StatusBadge status={state} />
          </p>
        </div>
        <div className="online-toggle">
          <span>{onDuty ? 'On duty' : 'Off duty'}</span>
          <ConfirmChange question={onDuty ? 'Go off duty?' : 'Go on duty?'} onConfirm={() => changeDuty(!onDuty)}>
            {(ask) => (
              <Switch
                checked={onDuty}
                label="On duty"
                onChange={() => {
                  if (state !== 'carrying') return ask();
                  const message = 'Deliver your order before going off duty.'; // R-DLV-03
                  logError(message); // every error shown is logged (R-DASH-06)
                  setActionError(message);
                }}
              />
            )}
          </ConfirmChange>
        </div>
      </section>

      {actionError && (
        <p className="alert alert-error" role="alert">
          {actionError}
        </p>
      )}

      {onDuty ? (
        <Empty icon="signal" title="No requests right now">
          You'll be offered an order when a restaurant marks one ready.
        </Empty>
      ) : (
        <Empty icon="moon" title="You're off duty">
          Go on duty to start receiving delivery offers.
        </Empty>
      )}
    </>
  );
}
