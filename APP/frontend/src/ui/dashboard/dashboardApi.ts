import { useSyncExternalStore } from 'react';

// Client-side log behind the output console panel.
// TODO: replace with fetch() to the real backend if actions/errors should also be audited server-side

export interface LogEntry {
  id: number;
  at: Date;
  kind: 'sent' | 'error';
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

/** Logs an action, runs it, and logs any error before rethrowing it so the caller can show it. */
export async function send<T>(action: string, run: () => Promise<T>): Promise<T> {
  logSent(action);
  try {
    return await run();
  } catch (e) {
    logError(`${action}: ${errorMessage(e)}`);
    throw e;
  }
}
