import { ApiError } from '@/lib/errors';
import { readCookie } from '@/lib/cookies';
import { mockApiFetch, mockMode } from '@/api/mockApi';

// '' in dev (Vite proxies /api to the backend, so requests are same-origin) and the real backend
// origin in production builds (see .env.production / deploy docs) where frontend and backend are
// fully separate origins and every request needs credentials + CORS.
export const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '';

// Dispatched when a request gets a 401 that a refresh could not recover from. A single listener in
// AppLayout redirects to /login - kept out of this module so it stays framework-router agnostic.
export const AUTH_EXPIRED_EVENT = 'hub:auth-expired';

let refreshInFlight: Promise<boolean> | null = null;

function csrfHeaders(json: boolean): Record<string, string> {
  const token = readCookie('XSRF-TOKEN');
  return {
    ...(json ? { 'Content-Type': 'application/json' } : {}),
    ...(token ? { 'X-XSRF-TOKEN': token } : {}),
  };
}

async function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = fetch(`${API_BASE}/api/auth/refresh`, {
      method: 'POST',
      credentials: 'include',
      headers: csrfHeaders(false),
    })
      .then((res) => res.ok)
      .catch(() => false)
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

interface ApiFetchOptions extends RequestInit {
  /** Internal: prevents an infinite refresh -> retry loop. */
  _retried?: boolean;
}

export async function apiFetch<T>(path: string, opts: ApiFetchOptions = {}): Promise<T> {
  if (mockMode) return mockApiFetch<T>(path, opts);

  const { _retried, ...init } = opts;
  const method = (init.method ?? 'GET').toUpperCase();
  const isBodyJson = init.body != null && !(init.body instanceof FormData);

  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    credentials: 'include',
    headers: {
      ...(method !== 'GET' && method !== 'HEAD' ? csrfHeaders(isBodyJson) : isBodyJson ? { 'Content-Type': 'application/json' } : {}),
      ...init.headers,
    },
  });

  if (res.status === 401 && !_retried && !path.startsWith('/api/auth/')) {
    const recovered = await refreshSession();
    if (recovered) return apiFetch<T>(path, { ...opts, _retried: true });
    window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT));
    throw new ApiError('로그인이 필요합니다.', 401, 'UNAUTHENTICATED');
  }

  if (!res.ok) throw await ApiError.fromResponse(res);
  if (res.status === 204) return undefined as T;

  const contentType = res.headers.get('content-type') ?? '';
  if (contentType.includes('application/json')) return (await res.json()) as T;
  return (await res.text()) as T;
}

export function apiGet<T>(path: string): Promise<T> {
  return apiFetch<T>(path);
}

export function apiPost<T>(path: string, body?: unknown): Promise<T> {
  return apiFetch<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) });
}

export function apiPut<T>(path: string, body?: unknown): Promise<T> {
  return apiFetch<T>(path, { method: 'PUT', body: body === undefined ? undefined : JSON.stringify(body) });
}

export function apiPatch<T>(path: string, body?: unknown): Promise<T> {
  return apiFetch<T>(path, { method: 'PATCH', body: body === undefined ? undefined : JSON.stringify(body) });
}

export function apiDelete<T>(path: string): Promise<T> {
  return apiFetch<T>(path, { method: 'DELETE' });
}

/** Multipart upload - never JSON-encode the body, the browser sets the boundary header itself. */
export function apiUpload<T>(path: string, formData: FormData, method: 'POST' | 'PUT' = 'POST'): Promise<T> {
  return apiFetch<T>(path, { method, body: formData });
}

/** Downloads a binary response and returns it as a Blob with its filename (from Content-Disposition). */
export async function apiDownload(
  path: string,
  headers?: Record<string, string>,
  retried = false,
): Promise<{ blob: Blob; filename: string | null }> {
  const res = await fetch(`${API_BASE}${path}`, { credentials: 'include', headers });
  if (res.status === 401 && !retried && !path.startsWith('/api/auth/')) {
    const recovered = await refreshSession();
    if (recovered) return apiDownload(path, headers, true);
    window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT));
    throw new ApiError('로그인이 필요합니다.', 401, 'UNAUTHENTICATED');
  }
  if (!res.ok) throw await ApiError.fromResponse(res);
  const disposition = res.headers.get('content-disposition') ?? '';
  const match = /filename\*=UTF-8''([^;]+)/.exec(disposition) ?? /filename="?([^";]+)"?/.exec(disposition);
  const filename = match ? decodeURIComponent(match[1]) : null;
  return { blob: await res.blob(), filename };
}
