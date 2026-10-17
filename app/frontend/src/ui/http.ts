// Thin fetch wrapper for the real backend. Nothing uses it yet: the *Api.ts files still serve mock data.
// When the backend is ready, each stub in regApi / ordApi should become one `request(...)` call
// (each stub has a TODO comment naming the endpoint it should call).

/** Base URL of the Spring Boot API. Set VITE_API_URL in .env (see .env.example). */
export const API_URL: string = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api';

/** Spring Security's default CSRF cookie and header names. */
const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';

const readCookie = (name: string) => document.cookie.split('; ').find((c) => c.startsWith(`${name}=`))?.slice(name.length + 1);

let onUnauthorized: () => void = () => {};

/** SessionProvider registers this so any 401 (session expired or missing) sends the user back to the login page. */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

/** A refused or failed request. Mirrors the backend's error body `{ status, code, message }`. */
export class ApiError extends Error {
  status: number;
  code?: string;
  constructor(message: string, status: number, code?: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

/** What the backend said about the last successful command, for the output console (see send() in dashboardApi.ts). */
interface ResponseNote {
  message?: string;
  eventId?: number | null;
}
let lastNote: ResponseNote | null = null;

/** Returns (and clears) the message / event id of the last successful response. */
export function takeResponseNote(): ResponseNote | null {
  const note = lastNote;
  lastNote = null;
  return note;
}

/** Successful commands come back wrapped as `{ data, message, eventId }` (CommandSuccess on the backend). */
const isEnvelope = (body: unknown): body is { data: unknown; message?: string; eventId?: number | null } =>
  typeof body === 'object' && body !== null && !Array.isArray(body) && 'data' in body && 'message' in body;

/**
 * JSON request. The session is Spring Security's own cookie (`credentials: 'include'`), and every
 * state-changing request echoes its CSRF token back in a header.
 * - Success: returns the payload, unwrapped from the `{ data, message, eventId }` envelope when there is one.
 *   The envelope's message and event id are kept for the console (R-DASH-06, R-ORD-05).
 * - Failure: throws an ApiError whose message is the backend's `message`, so UI components can show it as-is.
 */
export async function request<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const csrf = readCookie(CSRF_COOKIE);
  let response: Response;
  try {
    response = await fetch(`${API_URL}${path}`, {
      method,
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        ...(method !== 'GET' && csrf ? { [CSRF_HEADER]: decodeURIComponent(csrf) } : {}),
      },
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
  } catch {
    throw new ApiError('Cannot reach the server. Check that the backend is running.', 0, 'NETWORK');
  }
  const body = response.status === 204 ? null : await response.json().catch(() => null);
  if (response.status === 401 && !path.startsWith('/auth/')) onUnauthorized();
  if (!response.ok) throw new ApiError(body?.message ?? `Request failed (${response.status})`, response.status, body?.code);
  if (isEnvelope(body)) {
    lastNote = { message: body.message, eventId: body.eventId };
    return body.data as T;
  }
  return body as T;
}
