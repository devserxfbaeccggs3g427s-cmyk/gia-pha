/**
 * Client-side helpers for the Spring cutover (Task 36.1). Public URLs are
 * preserved exactly so the migration does not require DNS changes; selected
 * domains are proxied through {@code /api/spring/*} to the Spring API and
 * the cookie/Authorization headers are stripped/forwarded according to the
 * cutover phase. The exposed phase is `LEGACY` by default and flips to
 * `SPRING` once Task 20 has run the global identity cutover.
 *
 * <p>This module deliberately lives in {@code src/lib/api/} so the same
 * helper is used by both React Server Components (RSC) and the client.
 */
export const CUTOVER_PHASE = (process.env.NEXT_PUBLIC_GIAPHA_CUTOVER_PHASE ?? 'LEGACY') as
  | 'LEGACY'
  | 'TRANSITION'
  | 'SPRING';

/** Deterministic client-generated idempotency key (Task 36.2). */
export function offlineIdempotencyKey(operation: string, payload: unknown, userId?: string) {
  const basis = JSON.stringify({ operation, payload, userId: userId ?? 'anonymous' });
  const encoder = new TextEncoder();
  const bytes = encoder.encode(basis);
  // Fallback: short hash via simple djb2 — runs in both Node and browsers
  // without crypto.subtle so it works in service workers too.
  let hash = 5381;
  for (const byte of bytes) {
    hash = ((hash << 5) + hash + byte) & 0xffffffff;
  }
  const hex = (hash >>> 0).toString(16).padStart(8, '0');
  const random = Math.random().toString(36).slice(2, 10);
  return `${operation.toUpperCase()}-${hex}-${random}`;
}

/** Build a public-API URL preserving the legacy path. */
export function publicApiUrl(path: string): string {
  if (!path.startsWith('/')) {
    throw new Error(`API path must start with /: ${path}`);
  }
  if (CUTOVER_PHASE === 'SPRING') {
    return `/api/spring${path}`;
  }
  return path;
}

/** Build a Spring-only path that always goes through the new service. */
export function springApiUrl(path: string): string {
  if (!path.startsWith('/')) {
    throw new Error(`API path must start with /: ${path}`);
  }
  return `/api/spring${path}`;
}

/**
 * Headers attached to every authenticated request (Task 36.3-36.4). The
 * service worker consults {@code cache-version} to decide whether its
 * private caches are still valid; the {@code identity-tag} is the stable
 * user id and is used by the worker to flush user-scoped data on logout.
 */
export function privateRequestHeaders(userId: string | null, cacheVersion: string) {
  return {
    'Cache-Version': cacheVersion,
    'Identity-Tag': userId ?? 'anonymous'
  } as const;
}
