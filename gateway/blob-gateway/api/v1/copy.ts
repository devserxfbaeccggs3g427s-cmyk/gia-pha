/**
 * POST /api/v1/copy — controlled copy/promotion (Task 13.5).
 *
 * Spring uses this to promote a verified quarantine object to its final
 * pathname (upload flow step "Copy/promote to final path") and to feed the
 * archive replication job. Both source and destination must sit inside the
 * storage-layout allowlist, and the destination is never overwritten — a
 * promotion race surfaces as CONFLICT instead of silently replacing bytes.
 */

import { randomUUID } from 'node:crypto';

import { copy } from '@vercel/blob';

import { loadEnv } from '../../lib/env.js';
import { failFrom, ok } from '../../lib/errors.js';
import { logRequest, outcomeOf, pathHash } from '../../lib/log.js';
import { verifyServiceAuth } from '../../lib/service-auth.js';
import { requireJsonObject, requirePathname } from '../../lib/validate.js';

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

    const fromPathname = requirePathname(body.fromPathname, env);
    const toPathname = requirePathname(body.toPathname, env);
    hashedPath = pathHash(toPathname);

    const result = await copy(fromPathname, toPathname, {
      access: 'private',
      addRandomSuffix: false
    });

    response = ok({ pathname: result.pathname, contentType: result.contentType }, requestId);
  } catch (error) {
    response = failFrom(error, requestId);
  }
  logRequest({
    requestId,
    route: 'copy',
    ...(issuer !== undefined ? { issuer } : {}),
    ...(hashedPath !== undefined ? { pathHash: hashedPath } : {}),
    outcome: outcomeOf(response.status),
    status: response.status,
    durationMs: Date.now() - startedAt
  });
  return response;
}
