/**
 * POST /api/v1/list — authenticated, paginated listing (Task 13.5).
 *
 * Used by the Spring reconciler to enumerate quarantine/orphan objects and by
 * migration tooling. Returns pathnames and object metadata only — never the
 * private store URLs — so listing output cannot be turned into access.
 */

import { randomUUID } from 'node:crypto';

import { list } from '@vercel/blob';

import { loadEnv, type GatewayEnv } from '../../lib/env.js';
import { GatewayError, failFrom, ok } from '../../lib/errors.js';
import { logRequest, outcomeOf, pathHash } from '../../lib/log.js';
import { verifyServiceAuth } from '../../lib/service-auth.js';
import { requireJsonObject } from '../../lib/validate.js';

const MAX_LIMIT = 1000;

export async function POST(request: Request): Promise<Response> {
  const requestId = randomUUID();
  const startedAt = Date.now();
  const env = loadEnv();
  let issuer: string | undefined;
  let hashedPath: string | undefined;
  let response: Response;
  try {
    const rawBody = await request.text();
    issuer = verifyServiceAuth(request, rawBody, env).issuer;
    const body = requireJsonObject(rawBody);

    const prefix = requirePrefix(body.prefix, env);
    const limit = resolveLimit(body.limit);
    const cursor = optionalCursor(body.cursor);
    hashedPath = pathHash(prefix);

    const page = await list({ prefix, limit, ...(cursor ? { cursor } : {}) });
    response = ok(
      {
        blobs: page.blobs.map((blob) => ({
          pathname: blob.pathname,
          size: blob.size,
          uploadedAt: blob.uploadedAt
        })),
        hasMore: page.hasMore,
        cursor: page.hasMore ? page.cursor : null
      },
      requestId
    );
  } catch (error) {
    response = failFrom(error, requestId);
  }
  logRequest({
    requestId,
    route: 'list',
    ...(issuer !== undefined ? { issuer } : {}),
    ...(hashedPath !== undefined ? { pathHash: hashedPath } : {}),
    outcome: outcomeOf(response.status),
    status: response.status,
    durationMs: Date.now() - startedAt
  });
  return response;
}

/** Prefixes obey the same storage-layout allowlist as exact pathnames. */
function requirePrefix(value: unknown, env: GatewayEnv): string {
  if (typeof value !== 'string' || value.length === 0 || value.length > 1024) {
    throw new GatewayError('VALIDATION_ERROR', 'prefix is required and bounded');
  }
  if (value.startsWith('/') || value.includes('..') || /[\u0000-\u001f\u007f]/.test(value)) {
    throw new GatewayError('VALIDATION_ERROR', 'prefix is not a normalized relative path');
  }
  if (!env.allowedPathPrefixes.some((prefix) => value.startsWith(prefix))) {
    throw new GatewayError('FORBIDDEN', 'prefix is outside the allowed storage layout');
  }
  return value;
}

function resolveLimit(value: unknown): number {
  if (value === undefined || value === null) {
    return MAX_LIMIT;
  }
  if (typeof value !== 'number' || !Number.isInteger(value) || value < 1 || value > MAX_LIMIT) {
    throw new GatewayError('VALIDATION_ERROR', `limit must be an integer between 1 and ${MAX_LIMIT}`);
  }
  return value;
}

function optionalCursor(value: unknown): string | undefined {
  if (value === undefined || value === null) {
    return undefined;
  }
  if (typeof value !== 'string' || value.length === 0 || value.length > 4096) {
    throw new GatewayError('VALIDATION_ERROR', 'cursor must be a non-empty string');
  }
  return value;
}
