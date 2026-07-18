export interface ShareTokenPayload {
  version: 1;
  treeId: string;
  expiresAt: number;
  nonce: string;
  permission: 'VIEW';
}

export class ShareTokenError extends Error {
  constructor(public readonly code: 'INVALID' | 'EXPIRED' | 'CONFIGURATION') {
    super(code === 'EXPIRED' ? 'Share link has expired' : code === 'CONFIGURATION' ? 'Share link signing is not configured' : 'Share link is invalid');
    this.name = 'ShareTokenError';
  }
}

export async function createSignedShareToken(
  payload: ShareTokenPayload,
  secret: string | undefined
): Promise<string> {
  if (!secret) throw new ShareTokenError('CONFIGURATION');
  const encodedPayload = encodeBase64Url(new TextEncoder().encode(JSON.stringify(payload)));
  const signature = await sign(encodedPayload, secret);
  return `${encodedPayload}.${encodeBase64Url(signature)}`;
}

/** Edge-safe validation used by middleware before a public share request reaches a route. */
export async function verifySignedShareToken(
  token: string,
  secret: string | undefined,
  now = Date.now()
): Promise<ShareTokenPayload> {
  if (!secret) throw new ShareTokenError('CONFIGURATION');
  if (!/^[A-Za-z0-9_-]{20,400}\.[A-Za-z0-9_-]{43}$/.test(token)) throw new ShareTokenError('INVALID');
  const [encodedPayload, encodedSignature] = token.split('.');

  let payload: unknown;
  let signature: Uint8Array;
  try {
    payload = JSON.parse(new TextDecoder().decode(decodeBase64Url(encodedPayload)));
    signature = decodeBase64Url(encodedSignature);
  } catch {
    throw new ShareTokenError('INVALID');
  }

  const key = await importHmacKey(secret, ['verify']);
  const signatureBytes = new Uint8Array(signature.length);
  signatureBytes.set(signature);
  const validSignature = await crypto.subtle.verify('HMAC', key, signatureBytes, new TextEncoder().encode(encodedPayload));
  if (!validSignature || !isPayload(payload)) throw new ShareTokenError('INVALID');
  if (payload.expiresAt <= now) throw new ShareTokenError('EXPIRED');
  return payload;
}

async function sign(value: string, secret: string): Promise<Uint8Array> {
  const key = await importHmacKey(secret, ['sign']);
  return new Uint8Array(await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(value)));
}

function importHmacKey(secret: string, usages: KeyUsage[]): Promise<CryptoKey> {
  return crypto.subtle.importKey('raw', new TextEncoder().encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, usages);
}

function encodeBase64Url(bytes: Uint8Array): string {
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

function decodeBase64Url(value: string): Uint8Array {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(value.length / 4) * 4, '=');
  const binary = atob(base64);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

function isPayload(value: unknown): value is ShareTokenPayload {
  if (typeof value !== 'object' || value === null) return false;
  const payload = value as Partial<ShareTokenPayload>;
  return payload.version === 1
    && payload.permission === 'VIEW'
    && typeof payload.expiresAt === 'number'
    && Number.isSafeInteger(payload.expiresAt)
    && typeof payload.nonce === 'string'
    && /^[A-Za-z0-9_-]{16,128}$/.test(payload.nonce)
    && typeof payload.treeId === 'string'
    && /^[A-Za-z0-9_-]{1,128}$/.test(payload.treeId);
}
