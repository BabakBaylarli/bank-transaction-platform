import { config } from '@/config';

import type { ProblemDetail } from './types';

/** Thrown for every non-2xx response; carries the server's RFC 7807 problem detail. */
export class ApiError extends Error {
  readonly problem: ProblemDetail;

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${problem.status}`);
    this.name = 'ApiError';
    this.problem = problem;
  }

  get status() {
    return this.problem.status;
  }

  /** Human readable text for toasts, including field errors from validation problems. */
  get userMessage(): string {
    const fields = this.problem.errors?.map((e) => `${e.field}: ${e.message}`).join(' · ');
    return fields ? `${this.message} (${fields})` : this.message;
  }
}

type TokenProvider = () => string | null;
type UnauthorizedHandler = () => void;

let tokenProvider: TokenProvider = () => null;
let onUnauthorized: UnauthorizedHandler = () => {};

/** Wired once by the AuthProvider - keeps the client free of React dependencies. */
export function configureApiClient(provider: TokenProvider, unauthorized: UnauthorizedHandler) {
  tokenProvider = provider;
  onUnauthorized = unauthorized;
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  headers?: Record<string, string>;
  query?: Record<string, string | number | undefined | null>;
  signal?: AbortSignal;
}

export interface ApiResponse<T> {
  data: T;
  status: number;
  headers: Headers;
}

export function buildUrl(path: string, query?: RequestOptions['query']): string {
  const params = new URLSearchParams();
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value));
  });
  const qs = params.toString();
  return `${config.apiBaseUrl}${path}${qs ? `?${qs}` : ''}`;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<ApiResponse<T>> {
  const headers: Record<string, string> = { Accept: 'application/json, application/problem+json', ...options.headers };
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  const token = tokenProvider();
  if (token) headers.Authorization = `Bearer ${token}`;

  let response: Response;
  try {
    response = await fetch(buildUrl(path, options.query), {
      method: options.method ?? 'GET',
      headers,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
      signal: options.signal,
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e;
    throw new ApiError({ status: 0, title: 'Network error', detail: 'Cannot reach the server. Check your connection.' });
  }

  const text = await response.text();
  const json = text ? safeJson(text) : undefined;

  if (!response.ok) {
    const problem: ProblemDetail =
      json && typeof json === 'object'
        ? { status: response.status, ...(json as Partial<ProblemDetail>) }
        : { status: response.status, title: response.statusText, detail: `Request failed (${response.status})` };
    if (response.status === 401 && token) onUnauthorized();
    throw new ApiError(problem);
  }
  return { data: json as T, status: response.status, headers: response.headers };
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}
