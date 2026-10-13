import { useSyncExternalStore } from 'react';

// dashboardApi (a2: ui.dashboard): the output-console log, plus the data the dashboards display
// (customer order statuses, the restaurant's preparation queue).
// TODO: if the backend exposes CommandLog (OutputConsoleFeed), the console can read it instead of this client-side log.
import { db } from '../../mockData';
import type { Order } from '../../domain';

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
  try {
    const result = await run();
    logOk(action);
    return result;
  } catch (e) {
    logError(`${action}: ${errorMessage(e)}`);
    throw e;
  }
}

// ---------- Dashboard data ----------

const copy = <T>(value: T): T => structuredClone(value);

/** Customer's own orders with their current status (a2: CustomerOrderStatusView). */
// TODO: replace with fetch() to the real backend: GET /customers/{id}/orders
export async function getCustomerOrderStatuses(customerId: string): Promise<Order[]> {
  return copy(db.orders.filter((o) => o.customerId === customerId));
}

/**
 * The restaurant's preparation queue (a2: PreparationQueueView, R-ORD-03): accepted and preparing orders,
 * first in first out. The ordering belongs to the backend; the UI shows the list exactly as received.
 */
// TODO: replace with fetch() to the real backend: GET /restaurants/{id}/preparation-queue
export async function getPreparationQueue(restaurantId: string): Promise<Order[]> {
  return copy(
    db.orders
      .filter((o) => o.restaurantId === restaurantId && (o.status === 'accepted' || o.status === 'preparing'))
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt)),
  );
}
