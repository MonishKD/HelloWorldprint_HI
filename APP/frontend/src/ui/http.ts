// Thin fetch wrapper for the real backend. Nothing uses it yet: the *Api.ts files still serve mock data.
// When the backend is ready, each stub in regApi / ordApi should become one `request(...)` call
// (see docs/BACKEND_INTEGRATION.md for the endpoint each function expects).

/** Base URL of the Spring Boot API. Set VITE_API_URL in .env (see .env.example). */
export const API_URL: string = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api';

let authToken: string | null = null;

/** Call after login/logout so every later request carries the session token. */
export function setAuthToken(token: string | null) {
  authToken = token;
}

/**
 * JSON request. Throws an Error whose message is the backend's `message` field when present,
 * so UI components can show it as-is and the output console logs it (see send() in dashboardApi.ts).
 */
export async function request<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const response = await fetch(`${API_URL}${path}`, {
    method: options.method ?? 'GET',
    headers: {
      'Content-Type': 'application/json',
      ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
    },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });
  const data = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) throw new Error(data?.message ?? `Request failed (${response.status})`);
  return data as T;
}
