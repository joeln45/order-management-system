import type { ProblemDetail } from "./types";

/**
 * Base URL of the Spring Boot backend. Override at build/runtime via
 * NEXT_PUBLIC_API_URL (exposed to the browser) or API_URL (server-only).
 */
export const API_BASE =
  process.env.API_URL ?? process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  constructor(public status: number, public problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `HTTP ${status}`);
  }
}

type FetchOptions = RequestInit & { bearer?: string };

/**
 * Thin fetch wrapper that:
 *  - prefixes `API_BASE`
 *  - attaches `Authorization: Bearer <token>` when `bearer` is provided
 *  - parses JSON responses
 *  - surfaces non-2xx as ApiError, keeping the ProblemDetail body intact
 */
export async function apiFetch<T>(path: string, opts: FetchOptions = {}): Promise<T> {
  const { bearer, headers, ...rest } = opts;
  const res = await fetch(`${API_BASE}${path}`, {
    ...rest,
    headers: {
      "Content-Type": "application/json",
      ...(bearer ? { Authorization: `Bearer ${bearer}` } : {}),
      ...headers,
    },
    // Disable Next's fetch cache for API calls — data is dynamic per-user.
    cache: "no-store",
  });

  if (res.status === 204) return undefined as T;

  const text = await res.text();
  const body: unknown = text ? JSON.parse(text) : undefined;

  if (!res.ok) {
    throw new ApiError(res.status, (body ?? {}) as ProblemDetail);
  }
  return body as T;
}
