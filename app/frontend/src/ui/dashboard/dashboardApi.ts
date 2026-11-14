import { useSyncExternalStore } from 'react';

// dashboardApi (a2: ui.dashboard): the output-console log, plus the data the dashboards display
// (customer order statuses, the restaurant's preparation queue).
// The console shows the backend's command log (GET /dashboard/console, a2 OutputConsoleFeed) next to this browser-side log,
// which only keeps what the backend never records: what was just sent, sign-in/out, and errors that never reached a command.
import type { CityView, Order, OrderStatus } from '../../domain';
import { toCityView, toOrder } from '../adapters';
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

const commandListeners = new Set<() => void>();

/** OutputConsolePanel listens here to reload the backend's command log after each command (#140). */
export function onCommandFinished(listener: () => void) {
  commandListeners.add(listener);
  return () => {
    commandListeners.delete(listener);
  };
}

/**
 * Every user action goes through here (R-DASH-06): logs what was sent, then makes sure its outcome reaches the console,
 * and rethrows errors so the caller can show them.
 */
export async function send<T>(action: string, run: () => Promise<T>): Promise<T> {
  logSent(action);
  takeResponseNote(); // forget anything left over from an earlier request
  try {
    const result = await run();
    // A command answers in the { data, message, eventId } envelope and the backend logs it (CommandLog): the console
    // shows that entry. Anything else (sign-in, sign-out) is only logged here.
    if (takeResponseNote()) commandListeners.forEach((listener) => listener());
    else logOk(action);
    return result;
  } catch (e) {
    // ponytail: a refusal with a status is taken as logged by the backend; a 400 from malformed JSON isn't, but the UI never sends one.
    // A 401, a network failure or an error raised in the browser never reaches a command, so those are logged here.
    if (e instanceof ApiError && e.status >= 400 && e.status !== 401) commandListeners.forEach((listener) => listener());
    else logError(`${action}: ${errorMessage(e)}${e instanceof ApiError && e.code ? ` [${e.status} ${e.code}]` : ''}`);
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

/** "PlaceOrderCommand" -> "Place order" */
const commandLabel = (name: string) => name.replace(/Command$/, '').replace(/(?!^)([A-Z])/g, ' $1').replace(/^(.)(.*)$/, (_, a, b) => a + b.toLowerCase());

/**
 * Turns a backend log row into a console line: SUCCESS is OK, REFUSED and ERROR are shown as errors.
 * The time comes without a zone; the database clock is UTC (Docker), so it is read as UTC.
 */
export function toLogEntry(row: CommandLogRow): LogEntry {
  const text = row.statusMessage || row.statusCode;
  const at = /(Z|[+-]\d\d:\d\d)$/.test(row.createdAt) ? row.createdAt : `${row.createdAt}Z`;
  return {
    id: Number(row.id),
    at: new Date(at),
    kind: row.result === 'SUCCESS' ? 'ok' : 'error',
    message: `${commandLabel(row.commandName)}: ${text}${row.eventId != null ? ` · event #${row.eventId}` : ''}`,
  };
}

/** The backend sends the console 100 entries at a time. */
export const CONSOLE_PAGE = 100;

/** GET /dashboard/console: the signed-in user's newest command log entries, oldest first; `before` loads the page before that entry. */
export async function getConsoleFeed(before?: number): Promise<LogEntry[]> {
  const rows = await request<CommandLogRow[]>(`/dashboard/console${before ? `?before=${before}` : ''}`);
  return rows.map(toLogEntry);
}

// ---------- Dashboard data ----------

/**
 * GET /dashboard/orders (a2: CustomerOrderStatusView): the signed-in user's orders, oldest first.
 * A customer gets their own, restaurant staff get their restaurant's; the backend picks from the session.
 */
export async function getDashboardOrders(): Promise<Order[]> {
  return (await request<unknown[]>('/dashboard/orders')).map(toOrder);
}

/** GET /dashboard/city: restaurants with their order counts, and on-duty couriers. Every role can read it. */
export async function getCityView(): Promise<CityView> {
  return toCityView(await request('/dashboard/city'));
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
