/**
 * Stable error contract (Task 13.6). The gateway answers with the same
 * envelope family as the backend — `{ ok: false, error: { code, message } }`
 * — and a small, frozen code set so Spring's error translation never has to
 * parse prose. Messages never echo pathnames, tokens or signed URLs.
 */

export type GatewayErrorCode =
  | 'UNAUTHORIZED' // missing/invalid service signature
  | 'FORBIDDEN' // authenticated but outside policy (path prefix, issuer)
  | 'VALIDATION_ERROR' // malformed request
  | 'NOT_FOUND' // unknown route or absent blob
  | 'CONFLICT' // no-overwrite violation
  | 'RATE_LIMITED' // upstream 429 passthrough
  | 'UPSTREAM_ERROR' // Vercel Blob control API failure
  | 'INTERNAL'; // unexpected gateway failure

const STATUS_BY_CODE: Record<GatewayErrorCode, number> = {
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  VALIDATION_ERROR: 400,
  NOT_FOUND: 404,
  CONFLICT: 409,
  RATE_LIMITED: 429,
  UPSTREAM_ERROR: 502,
  INTERNAL: 500
};

export class GatewayError extends Error {
  readonly code: GatewayErrorCode;

  constructor(code: GatewayErrorCode, message: string) {
    super(message);
    this.name = 'GatewayError';
    this.code = code;
  }
}

export function ok(data: unknown, requestId: string, status = 200): Response {
  return json({ ok: true, data }, status, requestId);
}

export function fail(code: GatewayErrorCode, message: string, requestId: string): Response {
  return json({ ok: false, error: { code, message } }, STATUS_BY_CODE[code], requestId);
}

/** Maps any thrown value to a stable error response without leaking details. */
export function failFrom(error: unknown, requestId: string): Response {
  if (error instanceof GatewayError) {
    return fail(error.code, error.message, requestId);
  }
  const status = upstreamStatus(error);
  if (status === 404) return fail('NOT_FOUND', 'Blob not found', requestId);
  if (status === 409 || status === 412) return fail('CONFLICT', 'Blob operation conflict', requestId);
  if (status === 429) return fail('RATE_LIMITED', 'Blob store rate limit', requestId);
  if (status !== undefined) return fail('UPSTREAM_ERROR', 'Blob control API error', requestId);
  return fail('INTERNAL', 'Unexpected gateway error', requestId);
}

function upstreamStatus(error: unknown): number | undefined {
  if (typeof error !== 'object' || error === null) return undefined;
  const record = error as Record<string, unknown>;
  if (typeof record.status === 'number') return record.status;
  if (typeof record.statusCode === 'number') return record.statusCode;
  return undefined;
}

function json(body: unknown, status: number, requestId: string): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'no-store',
      'x-request-id': requestId
    }
  });
}
