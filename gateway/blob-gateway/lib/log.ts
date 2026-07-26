/**
 * Structured JSON logging. Design rule (design.md §Observability): no PII,
 * tokens, URLs or pathnames in log payloads. Pathnames are correlated via a
 * SHA-256 prefix ("pathHash") that Spring can recompute; credentials and
 * signed URLs must never reach this module.
 */

import { createHash } from 'node:crypto';

export interface LogFields {
  requestId: string;
  route: string;
  issuer?: string;
  operation?: string;
  pathHash?: string;
  outcome: 'ok' | 'client_error' | 'auth_error' | 'upstream_error' | 'internal_error';
  status: number;
  durationMs: number;
}

export function logRequest(fields: LogFields): void {
  console.log(JSON.stringify({ ts: new Date().toISOString(), ...fields }));
}

/** Stable pseudonymous correlation for a pathname; never log the pathname itself. */
export function pathHash(pathname: string): string {
  return createHash('sha256').update(pathname, 'utf8').digest('hex').slice(0, 16);
}

export function outcomeOf(status: number): LogFields['outcome'] {
  if (status < 400) return 'ok';
  if (status === 401 || status === 403) return 'auth_error';
  if (status < 500) return 'client_error';
  if (status === 502) return 'upstream_error';
  return 'internal_error';
}
