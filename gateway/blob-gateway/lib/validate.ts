/**
 * Request validation for the signing and control endpoints (Task 13.3).
 * The gateway is the last line of defense before Blob credentials, so every
 * constraint is enforced here even when Spring already validated upstream:
 * exact pathname, single operation, content-type allowlist, 10 MiB cap,
 * no-overwrite and narrow expiry.
 */

import { GatewayError } from './errors.js';
import type { GatewayEnv } from './env.js';

export const OPERATIONS = ['get', 'head', 'put', 'delete'] as const;
export type SignedOperation = (typeof OPERATIONS)[number];

const MAX_PATHNAME_LENGTH = 1024; // upload_intents.quarantine_object_path VARCHAR(1024)
const MIN_TTL_SECONDS = 10;

export function parseOperation(value: unknown): SignedOperation {
  if (typeof value === 'string' && (OPERATIONS as readonly string[]).includes(value)) {
    return value as SignedOperation;
  }
  throw new GatewayError('VALIDATION_ERROR', 'operation must be one of get, head, put, delete');
}

/**
 * Validates a blob pathname: printable, relative, normalized (no traversal,
 * no doubled or trailing slashes) and inside the configured prefix allowlist.
 * The gateway never derives pathnames itself — Spring generates them — but a
 * capability must never be issuable outside the storage layout.
 */
export function requirePathname(value: unknown, env: GatewayEnv): string {
  if (typeof value !== 'string' || value.length === 0 || value.length > MAX_PATHNAME_LENGTH) {
    throw new GatewayError('VALIDATION_ERROR', 'pathname is required and bounded');
  }
  if (
    value.startsWith('/') ||
    value.endsWith('/') ||
    value.includes('//') ||
    value.includes('..') ||
    value.includes('\\') ||
    /[\u0000-\u001f\u007f]/.test(value) ||
    value.includes('?') ||
    value.includes('#')
  ) {
    throw new GatewayError('VALIDATION_ERROR', 'pathname is not a normalized relative path');
  }
  if (!env.allowedPathPrefixes.some((prefix) => value.startsWith(prefix))) {
    throw new GatewayError('FORBIDDEN', 'pathname is outside the allowed storage layout');
  }
  return value;
}

/** PUT capabilities must carry an allowlisted content type. */
export function requireContentType(value: unknown, env: GatewayEnv): string {
  if (typeof value !== 'string' || value.length === 0) {
    throw new GatewayError('VALIDATION_ERROR', 'contentType is required for put operations');
  }
  const normalized = value.split(';', 1)[0]!.trim().toLowerCase();
  if (!env.allowedContentTypes.includes(normalized)) {
    throw new GatewayError('VALIDATION_ERROR', 'contentType is not allowed');
  }
  return normalized;
}

/** Upload size limit: callers may narrow the 10 MiB cap, never widen it. */
export function resolveMaxSizeBytes(value: unknown, env: GatewayEnv): number {
  if (value === undefined || value === null) {
    return env.maxPutBytes;
  }
  if (typeof value !== 'number' || !Number.isInteger(value) || value < 1) {
    throw new GatewayError('VALIDATION_ERROR', 'maxSizeBytes must be a positive integer');
  }
  if (value > env.maxPutBytes) {
    throw new GatewayError('VALIDATION_ERROR', 'maxSizeBytes exceeds the gateway limit');
  }
  return value;
}

/** Signed URL lifetime: bounded to [10s, GATEWAY_MAX_URL_TTL_SECONDS]. */
export function resolveTtlSeconds(value: unknown, env: GatewayEnv): number {
  if (value === undefined || value === null) {
    return env.maxUrlTtlSeconds;
  }
  if (typeof value !== 'number' || !Number.isInteger(value) || value < MIN_TTL_SECONDS) {
    throw new GatewayError('VALIDATION_ERROR', `ttlSeconds must be an integer >= ${MIN_TTL_SECONDS}`);
  }
  if (value > env.maxUrlTtlSeconds) {
    throw new GatewayError('VALIDATION_ERROR', 'ttlSeconds exceeds the gateway limit');
  }
  return value;
}

/** Optional opaque ETag precondition for conditional PUT/DELETE. */
export function optionalIfMatch(value: unknown): string | undefined {
  if (value === undefined || value === null) {
    return undefined;
  }
  if (typeof value !== 'string' || value.length === 0 || value.length > 256) {
    throw new GatewayError('VALIDATION_ERROR', 'ifMatch must be a non-empty string');
  }
  return value;
}

export function requireJsonObject(raw: string): Record<string, unknown> {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    throw new GatewayError('VALIDATION_ERROR', 'Request body must be valid JSON');
  }
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    throw new GatewayError('VALIDATION_ERROR', 'Request body must be a JSON object');
  }
  return parsed as Record<string, unknown>;
}
