/**
 * Service authentication between backend servers and the gateway
 * (design.md §Vercel Blob Design): every request is signed with
 * HMAC-SHA256 over method, canonical path, timestamp, nonce, body digest,
 * issuer and audience. Multiple verification keys are accepted at once so
 * key rotation overlaps without downtime (Task 13.6).
 *
 * Headers:
 *   x-giapha-timestamp  epoch milliseconds (±5 min skew accepted)
 *   x-giapha-nonce      caller-generated random string (replay guard)
 *   x-giapha-issuer     caller identity, must be in GATEWAY_EXPECTED_ISSUERS
 *   x-giapha-signature  hex HMAC-SHA256 of the canonical string
 */

import { createHash, createHmac, randomUUID, timingSafeEqual } from 'node:crypto';

import type { GatewayEnv } from './env.js';
import { GatewayError } from './errors.js';

const SIGNATURE_VERSION = 'giapha-v1';
const MAX_SKEW_MS = 5 * 60 * 1000;
const NONCE_CACHE_MAX = 10_000;

/** Best-effort per-instance replay guard; the timestamp window is the hard bound. */
const seenNonces = new Map<string, number>();

export interface VerifiedCaller {
  issuer: string;
}

/**
 * Verifies the inbound service signature. Throws {@link GatewayError}
 * (UNAUTHORIZED/FORBIDDEN) on any failure; error messages never include
 * header values.
 */
export function verifyServiceAuth(
  request: Request,
  rawBody: string,
  env: GatewayEnv
): VerifiedCaller {
  if (env.serviceKeys.length === 0) {
    // Fail closed: an unconfigured gateway authenticates nobody.
    throw new GatewayError('UNAUTHORIZED', 'Service authentication is not configured');
  }

  const timestamp = request.headers.get('x-giapha-timestamp');
  const nonce = request.headers.get('x-giapha-nonce');
  const issuer = request.headers.get('x-giapha-issuer');
  const signature = request.headers.get('x-giapha-signature');
  if (!timestamp || !nonce || !issuer || !signature) {
    throw new GatewayError('UNAUTHORIZED', 'Missing service authentication headers');
  }

  const timestampMs = Number.parseInt(timestamp, 10);
  if (!Number.isFinite(timestampMs) || Math.abs(Date.now() - timestampMs) > MAX_SKEW_MS) {
    throw new GatewayError('UNAUTHORIZED', 'Service signature timestamp outside window');
  }

  if (!env.expectedIssuers.includes(issuer)) {
    throw new GatewayError('FORBIDDEN', 'Unknown service issuer');
  }

  const canonical = canonicalString(
    request.method,
    new URL(request.url).pathname,
    timestamp,
    nonce,
    sha256Hex(rawBody),
    issuer,
    env.audience
  );

  const provided = safeHexBuffer(signature);
  const matches = env.serviceKeys.some((key) => {
    const expected = Buffer.from(createHmac('sha256', key).update(canonical).digest());
    return provided !== null && provided.length === expected.length
      && timingSafeEqual(provided, expected);
  });
  if (!matches) {
    throw new GatewayError('UNAUTHORIZED', 'Invalid service signature');
  }

  rejectReplay(`${issuer}:${nonce}`, timestampMs);
  return { issuer };
}

/** Signs an outbound request (gateway → Spring completion notification). */
export function signOutbound(
  method: string,
  url: string,
  body: string,
  issuer: string,
  audience: string,
  signingKey: string
): Record<string, string> {
  const timestamp = String(Date.now());
  const nonce = randomUUID();
  const canonical = canonicalString(
    method,
    new URL(url).pathname,
    timestamp,
    nonce,
    sha256Hex(body),
    issuer,
    audience
  );
  return {
    'x-giapha-timestamp': timestamp,
    'x-giapha-nonce': nonce,
    'x-giapha-issuer': issuer,
    'x-giapha-signature': createHmac('sha256', signingKey).update(canonical).digest('hex')
  };
}

function canonicalString(
  method: string,
  path: string,
  timestamp: string,
  nonce: string,
  bodyDigestHex: string,
  issuer: string,
  audience: string
): string {
  return [SIGNATURE_VERSION, method.toUpperCase(), path, timestamp, nonce, bodyDigestHex, issuer, audience].join('\n');
}

function sha256Hex(value: string): string {
  return createHash('sha256').update(value, 'utf8').digest('hex');
}

function safeHexBuffer(hex: string): Buffer | null {
  return /^[0-9a-f]{64}$/i.test(hex) ? Buffer.from(hex, 'hex') : null;
}

function rejectReplay(key: string, timestampMs: number): void {
  const cutoff = Date.now() - MAX_SKEW_MS;
  if (seenNonces.size > NONCE_CACHE_MAX) {
    for (const [nonce, seenAt] of seenNonces) {
      if (seenAt < cutoff) seenNonces.delete(nonce);
    }
  }
  if (seenNonces.has(key)) {
    throw new GatewayError('UNAUTHORIZED', 'Replayed service nonce');
  }
  seenNonces.set(key, timestampMs);
}
