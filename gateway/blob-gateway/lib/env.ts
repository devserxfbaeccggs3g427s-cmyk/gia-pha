/**
 * Typed access to gateway configuration. Every value is read lazily so a
 * misconfigured deployment fails the affected request with a stable error
 * instead of crashing the whole function at import time.
 *
 * Secret inventory (Requirement 15.3 — no secrets in code or logs):
 * - BLOB_READ_WRITE_TOKEN      Vercel-provided; ONLY the gateway holds it (ADR-006).
 * - BLOB_WEBHOOK_PUBLIC_KEY    verifies upload-completed callbacks (Task 13.4).
 * - GATEWAY_SERVICE_KEYS       comma-separated HMAC keys for inbound service auth;
 *                              all entries verify, so old + new overlap during rotation.
 * - GATEWAY_NOTIFY_KEYS        HMAC keys for signing outbound completion notifications
 *                              to Spring; index 0 signs, all verify on the Spring side.
 */

const DEFAULT_MAX_PUT_BYTES = 10 * 1024 * 1024; // legacy media cap: 10 MiB (Req 7.2)
const DEFAULT_MAX_URL_TTL_SECONDS = 300; // signed URLs expire within 5 minutes (ADR-006)
const DEFAULT_CONTENT_TYPES = 'image/jpeg,image/png,image/webp,application/pdf';
const DEFAULT_PATH_PREFIXES = 'quarantine/,media/,artifacts/,backups/,archive/';

export interface GatewayEnv {
  /** HMAC keys accepted for inbound service signatures (rotation overlap). */
  serviceKeys: string[];
  /** Issuers allowed to call the gateway (e.g. giapha-spring, giapha-next). */
  expectedIssuers: string[];
  /** Audience this gateway answers to; must appear in every signature. */
  audience: string;
  /** Base URL of this deployment, used to register upload callbacks. */
  publicUrl: string | undefined;
  /** Spring endpoint receiving forwarded upload-completed notifications. */
  springCallbackUrl: string | undefined;
  /** Keys for signing outbound notifications; index 0 signs. */
  notifyKeys: string[];
  /** Pathname prefixes the gateway will ever sign or mutate. */
  allowedPathPrefixes: string[];
  /** Content types accepted on PUT capabilities. */
  allowedContentTypes: string[];
  /** Hard upload cap; requests may narrow it, never widen it. */
  maxPutBytes: number;
  /** Hard cap on signed URL lifetime, in seconds. */
  maxUrlTtlSeconds: number;
}

export function loadEnv(): GatewayEnv {
  return {
    serviceKeys: splitList(process.env.GATEWAY_SERVICE_KEYS),
    expectedIssuers: splitList(process.env.GATEWAY_EXPECTED_ISSUERS ?? 'giapha-spring,giapha-next'),
    audience: process.env.GATEWAY_AUDIENCE ?? 'giapha-blob-gateway',
    publicUrl: trimTrailingSlash(process.env.GATEWAY_PUBLIC_URL),
    springCallbackUrl: process.env.GATEWAY_SPRING_CALLBACK_URL,
    notifyKeys: splitList(process.env.GATEWAY_NOTIFY_KEYS),
    allowedPathPrefixes: splitList(process.env.GATEWAY_ALLOWED_PATH_PREFIXES ?? DEFAULT_PATH_PREFIXES),
    allowedContentTypes: splitList(
      (process.env.GATEWAY_ALLOWED_CONTENT_TYPES ?? DEFAULT_CONTENT_TYPES).toLowerCase()
    ),
    maxPutBytes: parsePositiveInt(process.env.GATEWAY_MAX_PUT_BYTES, DEFAULT_MAX_PUT_BYTES),
    maxUrlTtlSeconds: parsePositiveInt(
      process.env.GATEWAY_MAX_URL_TTL_SECONDS,
      DEFAULT_MAX_URL_TTL_SECONDS
    )
  };
}

function splitList(value: string | undefined): string[] {
  return (value ?? '')
    .split(',')
    .map((entry) => entry.trim())
    .filter((entry) => entry.length > 0);
}

function parsePositiveInt(value: string | undefined, fallback: number): number {
  const parsed = Number.parseInt(value ?? '', 10);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

function trimTrailingSlash(value: string | undefined): string | undefined {
  return value?.replace(/\/+$/, '');
}
