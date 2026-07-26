/**
 * POST /api/v1/upload-callback — signature-verified upload completion hook
 * (Task 13.4, Req 7.8).
 *
 * Vercel Blob calls this endpoint after a browser finishes a presigned PUT.
 * `handleUploadPresigned` verifies the request signature against
 * `BLOB_WEBHOOK_PUBLIC_KEY` before we act on it, so forged notifications are
 * rejected without touching Spring. Verified events are forwarded to the
 * Spring completion endpoint with an outbound HMAC signature; a non-2xx
 * forward makes this handler fail so Blob retries the callback — Spring's
 * verify step stays the single writer of upload-intent state.
 */

import { randomUUID } from 'node:crypto';

import { handleUploadPresigned, type HandleUploadPresignedBody } from '@vercel/blob/client';

import { loadEnv, type GatewayEnv } from '../../lib/env.js';
import { GatewayError, failFrom, ok } from '../../lib/errors.js';
import { logRequest, outcomeOf, pathHash } from '../../lib/log.js';
import { signOutbound } from '../../lib/service-auth.js';

export async function POST(request: Request): Promise<Response> {
  const requestId = randomUUID();
  const startedAt = Date.now();
  const env = loadEnv();
  let hashedPath: string | undefined;
  let response: Response;
  try {
    const body = (await request.json()) as HandleUploadPresignedBody;

    await handleUploadPresigned({
      body,
      request,
      getSignedToken: () => {
        throw new GatewayError('FORBIDDEN', 'Token generation is not supported on this endpoint');
      },
      onUploadCompleted: async ({ blob, tokenPayload }) => {
        hashedPath = pathHash(blob.pathname);
        await forwardToSpring(blob.pathname, tokenPayload, env, requestId);
      }
    });

    response = ok({ received: true }, requestId);
  } catch (error) {
    response = failFrom(error, requestId);
  }
  logRequest({
    requestId,
    route: 'upload-callback',
    ...(hashedPath !== undefined ? { pathHash: hashedPath } : {}),
    outcome: outcomeOf(response.status),
    status: response.status,
    durationMs: Date.now() - startedAt
  });
  return response;
}

/**
 * Forwards the verified completion event to Spring, HMAC-signed with the
 * first notify key. Spring correlates the intent via `tokenPayload` (the
 * opaque string it supplied when requesting the PUT capability) and never
 * trusts the notification alone — it re-verifies the object out of band.
 */
async function forwardToSpring(
  pathname: string,
  tokenPayload: string | null | undefined,
  env: GatewayEnv,
  requestId: string
): Promise<void> {
  const signingKey = env.notifyKeys[0];
  if (env.springCallbackUrl === undefined || signingKey === undefined) {
    throw new GatewayError('INTERNAL', 'Completion forwarding is not configured');
  }
  const body = JSON.stringify({
    pathname,
    tokenPayload: tokenPayload ?? null,
    completedAt: new Date().toISOString()
  });
  const headers = signOutbound(
    'POST',
    env.springCallbackUrl,
    body,
    'giapha-blob-gateway',
    'giapha-spring',
    signingKey
  );
  const springResponse = await fetch(env.springCallbackUrl, {
    method: 'POST',
    headers: {
      ...headers,
      'content-type': 'application/json; charset=utf-8',
      'x-request-id': requestId
    },
    body
  });
  if (!springResponse.ok) {
    // Throwing fails the callback so Vercel Blob retries the notification.
    throw new GatewayError('UPSTREAM_ERROR', 'Spring completion endpoint rejected the event');
  }
}
