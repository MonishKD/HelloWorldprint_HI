import { useSyncExternalStore } from 'react';

// dashboardApi (a2: ui.dashboard): the output-console log, plus the data the dashboards display
// (customer order statuses, the restaurant's preparation queue).
// TODO: the backend's own command log (GET /dashboard/console, OutputConsoleFeed) could feed the console too.
import type { Order, OrderStatus } from '../../domain';
import { toOrder } from '../adapters';
import { ApiError, request, takeResponseNote } from '../http';

export interface LogEntry {
  id: number;
  at: Date;
  kind: 'sent' | 'ok' | 'error';
  message: string;
}

let logs: LogEntry[] = [];
let nextId = 1;
const listeners = new Set<() => void>();

function emit() {
  listeners.forEach((listener) => listener());
}

function push(kind: LogEntry['kind'], message: string) {
  logs = [...logs, { id: nextId++, at: new Date(), kind, message }];
  emit();
}

export const logSent = (message: string) => push('sent', message);
export const logOk = (message: string) => push('ok', message);
export const logError = (message: string) => push('error', message);

export function clearLogs() {
  logs = [];
  emit();
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function useLogs() {
  return useSyncExternalStore(subscribe, () => logs);
}

export function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

/**
 * Every user action goes through here (R-DASH-06): logs what was sent, then the response (OK or the error),
 * and rethrows errors so the caller can show them.
 */
export async function send<T>(action: string, run: () => Promise<T>): Promise<T> {
  logSent(action);
  takeResponseNote(); // forget anything left over from an earlier request
  try {
    const result = await run();
    // With the real backend, show what it answered ("Order accepted") and the transition's event id (R-ORD-05).
    const note = takeResponseNote();
    const detail = [note?.message, note?.eventId != null ? `event #${note.eventId}` : ''].filter(Boolean).join(' · ');
    logOk(detail ? `${action}: ${detail}` : action);
    return result;
  } catch (e) {
    const code = e instanceof ApiError && e.code ? ` [${e.status} ${e.code}]` : '';
    logError(`${action}: ${errorMessage(e)}${code}`);
    throw e;
  }
}

// ---------- Console feed from the backend (a2: CommandLog -> OutputConsoleFeed, #140) ----------

/** One row of the backend's command log table (`command_log`). */
export interface CommandLogRow {
  id: number | string;
  createdAt: string;
  commandName: string;
  result: 'SUCCESS' | 'REFUSED' | 'ERROR';
  status: number;
  statusCode: string;
  statusMessage?: string | null;
  eventId?: number | null;
}

/** Turns a backend log row into a console line: SUCCESS is OK, REFUSED and ERROR are shown as errors. */
export function toLogEntry(row: CommandLogRow): LogEntry {
  const text = row.statusMessage || row.statusCode;
  return {
    id: Number(row.id),
    at: new Date(row.createdAt),
    kind: row.result === 'SUCCESS' ? 'ok' : 'error',
    message: `${row.commandName}: ${text}${row.eventId != null ? ` (event #${row.eventId})` : ''}`,
  };
}
// TODO: when #140 exposes the feed, load it with request<CommandLogRow[]>('/console') and show it in OutputConsolePanel
// next to (or instead of) the client-side log above.

// ---------- Dashboard data ----------

/**
 * GET /dashboard/orders (a2: CustomerOrderStatusView): the signed-in user's orders, oldest first.
 * A customer gets their own, restaurant staff get their restaurant's; the backend picks from the session.
 */
export async function getDashboardOrders(): Promise<Order[]> {
  return (await request<unknown[]>('/dashboard/orders')).map(toOrder);
}

/** One row of the preparation queue (a2: PreparationQueueView): position 1 is next. Order details are not part of it. */
export interface QueueSlot {
  position: number;
  orderId: string;
  status: OrderStatus;
}

/**
 * GET /restaurants/{id}/queue (R-ORD-03): accepted orders, first accepted first prepared.
 * The ordering belongs to the backend and the UI keeps it as received; only the first order may start preparing.
 */
export async function getPreparationQueue(restaurantId: string): Promise<QueueSlot[]> {
  return request<QueueSlot[]>(`/restaurants/${restaurantId}/queue`);
}
