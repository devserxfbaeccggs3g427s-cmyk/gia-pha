import { createPrivateKey, createPublicKey, randomUUID } from 'node:crypto';

/**
 * Server-only ES256 (P-256) JWT helpers used by the Next.js bridge exchange
 * endpoint (Task 18, ADR-009).
 *
 * <p>The signing key is read from {@code BRIDGE_PRIVATE_KEY_PEM} and may
 * rotate via {@code BRIDGE_KEY_ID} (multiple keys are accepted on the
 * Spring side via the {@code kid} header).
 *
 * <p>Tokens are minted at most {@link MAX_LIFETIME_SECONDS} seconds (Req 2.5:
 * "tối đa năm phút") with a fresh {@code jti} on every call. The browser must
 * never persist them; the cookie set by {@link setBridgeCookie} is
 * {@code SameSite=Lax}, {@code HttpOnly}, {@code Secure} in production and
 * expires with the token.
 */

const MAX_LIFETIME_SECONDS = 5 * 60;

export type BridgeAuthStrength = 'SESSION' | 'REAUTHENTICATED' | 'HARDWARE';

export interface BridgeClaims {
  sub: string;
  scopes?: string[];
  strength?: BridgeAuthStrength;
}

interface SignedBridgeToken {
  token: string;
  tokenId: string;
  expiresAt: number;
}

/** Convert a raw EC private key into a JWK-style (d, x, y) coordinate set. */
export function exportBridgePrivateJwk(): { kid: string; d: string; x: string; y: string } | null {
  const pem = process.env.BRIDGE_PRIVATE_KEY_PEM;
  const kid = process.env.BRIDGE_KEY_ID;
  if (!pem || !kid) return null;
  const privateKey = createPrivateKey({ key: pem, format: 'pem' }).export({ format: 'jwk' }) as {
    d?: string;
    x?: string;
    y?: string;
  };
  if (!privateKey.d || !privateKey.x || !privateKey.y) return null;
  return { kid, d: privateKey.d, x: privateKey.x, y: privateKey.y };
}

/** Read the public-key JWK set used by Spring to verify the bridge token. */
export function exportBridgePublicJwks(): Array<{ kid: string; x: string; y: string }> {
  const pem = process.env.BRIDGE_PUBLIC_KEY_PEM;
  const kid = process.env.BRIDGE_KEY_ID;
  if (!pem || !kid) return [];
  const jwk = createPublicKey({ key: pem, format: 'pem' }).export({ format: 'jwk' }) as {
    x?: string;
    y?: string;
  };
  if (!jwk.x || !jwk.y) return [];
  return [{ kid, x: jwk.x, y: jwk.y }];
}

/**
 * Mint a bridge token for the supplied claims. The caller must already have
 * validated the encrypted NextAuth session — this helper only shapes the
 * downstream JWT.
 */
export function issueBridgeToken(claims: BridgeClaims, now = Date.now()): SignedBridgeToken {
  const pem = process.env.BRIDGE_PRIVATE_KEY_PEM;
  const kid = process.env.BRIDGE_KEY_ID;
  const issuer = process.env.BRIDGE_ISSUER;
  const audience = process.env.BRIDGE_AUDIENCE;
  if (!pem || !kid || !issuer || !audience) {
    throw new Error('Bridge token configuration missing');
  }
  const keyObject = createPrivateKey({ key: pem, format: 'pem' });
  const iat = Math.floor(now / 1000);
  const exp = iat + MAX_LIFETIME_SECONDS;
  const jti = randomUUID();
  const payload = {
    iss: issuer,
    aud: audience,
    sub: claims.sub,
    iat,
    nbf: iat,
    exp,
    jti,
    str: claims.strength ?? 'SESSION',
    ...(claims.scopes && claims.scopes.length > 0 ? { scope: claims.scopes.join(' ') } : {})
  };
  const header = { alg: 'ES256', typ: 'JWT', kid };
  const token = signJose(header, payload, keyObject);
  return { token, tokenId: jti, expiresAt: exp };
}

function signJose(header: Record<string, unknown>, payload: Record<string, unknown>,
  key: ReturnType<typeof createPrivateKey>): string {
  const headerSegment = base64url(JSON.stringify(header));
  const payloadSegment = base64url(JSON.stringify(payload));
  const signingInput = `${headerSegment}.${payloadSegment}`;
  const crypto = require('node:crypto') as typeof import('node:crypto');
  const signature = crypto.createSign('SHA256').update(signingInput).end().sign({
    key,
    dsaEncoding: 'ieee-p1363'
  });
  return `${signingInput}.${base64urlFromBuffer(signature)}`;
}

function base64url(input: string): string {
  return base64urlFromBuffer(Buffer.from(input, 'utf8'));
}

function base64urlFromBuffer(buffer: Buffer): string {
  return buffer.toString('base64').replace(/=+$/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}

export const BRIDGE_COOKIE_NAME = 'gp-bridge';

export interface BridgeCookieOptions {
  secure: boolean;
}

/** Build the {@code Set-Cookie} value for the bridge token. */
export function setBridgeCookie(signed: SignedBridgeToken,
  options: BridgeCookieOptions): string {
  const maxAge = Math.max(0, signed.expiresAt - Math.floor(Date.now() / 1000));
  const parts = [
    `${BRIDGE_COOKIE_NAME}=${signed.token}`,
    'Path=/api',
    'HttpOnly',
    'SameSite=Lax',
    `Max-Age=${maxAge}`
  ];
  if (options.secure) parts.push('Secure');
  return parts.join('; ');
}

/** Build the {@code Set-Cookie} value that clears the bridge cookie. */
export function clearBridgeCookie(options: BridgeCookieOptions): string {
  const parts = [
    `${BRIDGE_COOKIE_NAME}=`,
    'Path=/api',
    'HttpOnly',
    'SameSite=Lax',
    'Max-Age=0'
  ];
  if (options.secure) parts.push('Secure');
  return parts.join('; ');
}

/** Maximum bridge lifetime in seconds (Task 18 / Req 2.5). */
export const BRIDGE_MAX_LIFETIME_SECONDS = MAX_LIFETIME_SECONDS;
