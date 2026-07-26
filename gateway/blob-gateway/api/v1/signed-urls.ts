/**
 * POST /api/v1/signed-urls — issues an exact-path, single-operation,
 * short-expiry signed URL (Tasks 13.2/13.3, Req 7.5-7.7).
 *
 * The capability is double-scoped: the delegation token from
 * `issueSignedToken` is already restricted to one pathname and one
 * operation, and the presigned URL narrows it further (expiry, content type,
 * size, no-overwrite). A capability can therefore never authorize another
 * path or operation, and bytes never flow through this function — a 10 MiB
 * browser upload goes straight to Blob storage (Req 7.5).
 */

import { randomUUID } from 'node:crypto';

import { issueSignedToken, presignUrl } from '@vercel/blob';

import { loadEnv } from '../../lib/env.js';
import { GatewayError, failFrom, ok } from '../../lib/errors.js';
import { logRequest, outcomeOf, pathHash } from '../../lib/log.js';
import { verifyServiceAuth } from '../../lib/service-auth.js';
import {
  optionalIfMatch,
  parseOperation,
  requireContentType,
  requireJsonObject,
  requirePathname,
  resolveMaxSizeBytes,
  resolveTtlSeconds
} from '../../lib/validate.js';

const MAX_CALLBACK_PAYLOAD = 2048;

export async function POST(request: Request): Promise<Response> {
  const requestId = randomUUID();
  const startedAt = Date.now();
  const env = loadEnv();
  let issuer: string | undefined;
  let operation: string | undefined;
  let hashedPath: string | undefined;
  let response: Response;
  try {
    const rawBody = await request.text();
    issuer = verifyServiceAuth(request, rawBody, env).issuer;
    const body = requireJsonObject(rawBody);

    const op = parseOperation(body.operation);
    const pathname = requirePathname(body.pathname, env);
    const ttlSeconds = resolveTtlSeconds(body.ttlSeconds, env);
    operation = op;
    hashedPath = pathHash(pathname);

    const validUntil = Date.now() + ttlSeconds * 1000;
    const putConstraints =
      op === 'put'
        ? {
            allowedContentTypes: [requireContentType(body.contentType, env)],
            maximumSizeInBytes: resolveMaxSizeBytes(body.maxSizeBytes, env)
          }
        : undefined;

    // Delegation: exactly this pathname, exactly this operation, narrow expiry.
    const token = await issueSignedToken({
      pathname,
      operations: [op],
      validUntil,
      ...(putConstraints ?? {})
    });

    const { presignedUrl } = await presignUrl(token, {
      operation: op,
      pathname,
      access: 'private',
      validUntil,
      ...(op === 'put'
        ? {
            ...putConstraints,
            addRandomSuffix: false, // server-generated exact path (Req 7.4)
            allowOverwrite: false, // no-overwrite, always (Task 13.3)
            ...uploadCallback(body.callbackPayload, env)
          }
        : {}),
      ...(op === 'get' ? { useCache: body.useCache === true } : {}),
      ...(op === 'delete' ? withIfMatch(body.ifMatch) : {})
    });

    response = ok(
      {
        url: presignedUrl,
        pathname,
        operation: op,
        expiresAt: new Date(validUntil).toISOString(),
        ...(putConstraints ?? {})
      },
      requestId
    );
  } catch (error) {
    response = failFrom(error, requestId);
  }
  logRequest({
    requestId,
    route: 'signed-urls',
    ...(issuer !== undefined ? { issuer } : {}),
    ...(operation !== undefined ? { operation } : {}),
    ...(hashedPath !== undefined ? { pathHash: hashedPath } : {}),
    outcome: outcomeOf(response.status),
    status: response.status,
    durationMs: Date.now() - startedAt
  });
  return response;
}

/**
 * Registers the gateway's own callback endpoint on PUT capabilities so Blob
 * storage notifies us (signature-verified, Task 13.4) when the browser
 * finishes uploading. `callbackPayload` is an opaque Spring-provided string
 * (e.g. the upload-intent id) echoed back in the notification.
 */
function uploadCallback(callbackPayload: unknown, env: ReturnType<typeof loadEnv>) {
  if (env.publicUrl === undefined) {
    return {};
  }
  if (callbackPayload === undefined || callbackPayload === null) {
    return {
      onUploadCompleted: { callbackUrl: `${env.publicUrl}/api/v1/upload-callback` }
    };
  }
  if (typeof callbackPayload !== 'string' || callbackPayload.length > MAX_CALLBACK_PAYLOAD) {
    throw new GatewayError('VALIDATION_ERROR', 'callbackPayload must be a bounded string');
  }
  return {
    onUploadCompleted: {
      callbackUrl: `${env.publicUrl}/api/v1/upload-callback`,
      tokenPayload: callbackPayload
    }
  };
}

function withIfMatch(value: unknown) {
  const ifMatch = optionalIfMatch(value);
  return ifMatch === undefined ? {} : { ifMatch };
}
