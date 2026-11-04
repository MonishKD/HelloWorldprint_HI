// dlvApi (a2: ui.dlv): every courier call to the backend.
// The courier routes don't exist yet (#245), so these are stubs holding the state in the browser: a page reload goes
// back to offline. Each function is one future REST call; only its body changes when the backend is connected.
import type { CourierState } from '../../domain';

let state: CourierState = 'offline';

/** TODO(#245): GET the signed-in courier's state. */
export async function getCourierState(): Promise<CourierState> {
  return state;
}

/** TODO(#245): POST the duty command. The backend refuses going off duty while carrying (R-DLV-03). */
export async function setOnDuty(onDuty: boolean): Promise<void> {
  state = onDuty ? 'idle' : 'offline';
}
