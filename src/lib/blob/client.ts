import { del, get, list, put } from '@vercel/blob';

export type BlobStorageErrorCode =
  | 'CONFIGURATION'
  | 'NETWORK'
  | 'RATE_LIMIT'
  | 'STORAGE_FULL'
  | 'NOT_FOUND'
  | 'FORBIDDEN'
  | 'CONFLICT';

const DATA_BLOB_ACCESS = 'private' as const;

export class BlobStorageError extends Error {
  readonly code: BlobStorageErrorCode;
  readonly cause?: unknown;

  constructor(code: BlobStorageErrorCode, message: string, cause?: unknown) {
    super(message);
    this.name = 'BlobStorageError';
    this.code = code;
    this.cause = cause;
  }
}

export const BLOB_PATHS = {
  users: () => 'data/users.json',
  trees: () => 'data/trees.json',
  members: (treeId: string) => `data/trees/${treeId}/members.json`,
  relationships: (treeId: string) => `data/trees/${treeId}/relationships.json`,
  events: (treeId: string) => `data/trees/${treeId}/events.json`,
  mediaMetadata: (treeId: string) => `data/trees/${treeId}/media-metadata.json`,
  albums: (treeId: string) => `data/trees/${treeId}/albums.json`,
  mediaOriginal: (treeId: string, filename: string) => `media/${treeId}/originals/${filename}`,
  mediaThumbnail: (treeId: string, filename: string) => `media/${treeId}/thumbnails/${filename}`,
  changeLogs: (treeId: string) => `data/trees/${treeId}/change-logs.json`,
  backup: (treeId: string, timestamp: string) => `backups/${treeId}/${timestamp}.json`,
  backupPrefix: (treeId: string) => `backups/${treeId}/`,
  shareLinks: (treeId: string) => `data/trees/${treeId}/share-links.json`,
  shareLink: (token: string) => `share-links/${token}.json`,
  // ── Composite tree paths ─────────────────────────────────────────────────
  /** Primary config blob for a CompositeTree. This is the single source of
   *  truth for all composite configuration; source tree blobs are never
   *  written from composite operations. */
  compositeConfig: (treeId: string) => `data/trees/${treeId}/composite-config.json`,
  /** Append-only audit log for all composite config mutations. */
  compositeChangeLogs: (treeId: string) => `data/trees/${treeId}/composite-change-logs.json`,
  /** Disposable resolved manifest keyed by audience hash.  Must be treated as
   *  a cache; it is not a durable backup of domain data. */
  compositeManifest: (treeId: string, audienceHash: string) =>
    `cache/trees/${treeId}/resolved/${audienceHash}.json`,
  /** Prefix used to list and bulk-delete all resolved manifests for a tree. */
  compositeManifestPrefix: (treeId: string) => `cache/trees/${treeId}/resolved/`
} as const;

export interface BlobMetadata {
  pathname: string;
  url: string;
  size: number;
  uploadedAt: Date;
  contentType?: string;
}

export interface StoredBinaryBlob {
  url: string;
  pathname: string;
}

export async function withBlobErrorHandling<T>(
  operation: () => Promise<T>,
  context = 'Vercel Blob operation'
): Promise<T> {
  try {
    return await operation();
  } catch (error) {
    if (error instanceof BlobStorageError) {
      throw error;
    }

    throw normalizeBlobError(error, context);
  }
}

export async function readBlob<T>(path: string): Promise<T | null> {
  return withBlobErrorHandling(async () => {
    assertBlobCredentials();
    const result = await get(path, {
      access: DATA_BLOB_ACCESS,
      // Authentication and authorization data must never be served from a stale CDN entry.
      useCache: false
    });

    if (!result) {
      return null;
    }

    if (result.statusCode !== 200 || !result.stream) {
      throw new BlobStorageError('NETWORK', `Unexpected response while reading blob "${path}"`);
    }

    return (await new Response(result.stream).json()) as T;
  }, `Read blob "${path}"`);
}

export async function writeBlob<T>(path: string, data: T): Promise<void> {
  await withBlobErrorHandling(async () => {
    assertBlobCredentials();
    await put(path, JSON.stringify(data, null, 2), {
      access: DATA_BLOB_ACCESS,
      addRandomSuffix: false,
      allowOverwrite: true,
      cacheControlMaxAge: 60,
      contentType: 'application/json; charset=utf-8'
    });
  }, `Write blob "${path}"`);
}

/** Lists every blob under a prefix, following Vercel Blob cursors when needed. */
export async function listBlobs(prefix: string): Promise<BlobMetadata[]> {
  return withBlobErrorHandling(async () => {
    assertBlobCredentials();
    const blobs: BlobMetadata[] = [];
    let cursor: string | undefined;

    do {
      const page = await list({ prefix, ...(cursor ? { cursor } : {}), limit: 1000 });
      blobs.push(
        ...page.blobs.map((blob) => ({
          pathname: blob.pathname,
          url: blob.url,
          size: blob.size,
          uploadedAt: blob.uploadedAt
        }))
      );
      cursor = page.hasMore ? page.cursor : undefined;
    } while (cursor);

    return blobs;
  }, `List blobs with prefix "${prefix}"`);
}

export async function writeBinaryBlob(
  path: string,
  body: Blob | Buffer | ArrayBuffer,
  contentType: string
): Promise<StoredBinaryBlob> {
  return withBlobErrorHandling(async () => {
    assertBlobCredentials();
    const result = await put(path, body, {
      access: DATA_BLOB_ACCESS,
      addRandomSuffix: false,
      allowOverwrite: false,
      cacheControlMaxAge: 31_536_000,
      contentType
    });
    return { url: result.url, pathname: result.pathname };
  }, `Write binary blob "${path}"`);
}

export async function deleteBlob(path: string): Promise<void> {
  await withBlobErrorHandling(async () => {
    assertBlobCredentials();
    await del(path);
  }, `Delete blob "${path}"`);
}

export async function deleteBlobs(paths: readonly string[]): Promise<void> {
  if (paths.length === 0) return;
  await withBlobErrorHandling(async () => {
    assertBlobCredentials();
    await del([...paths]);
  }, `Delete ${paths.length} blobs`);
}

export function hasBlobCredentials(): boolean {
  return Boolean(
    process.env.BLOB_READ_WRITE_TOKEN ||
      (process.env.VERCEL_OIDC_TOKEN && process.env.BLOB_STORE_ID)
  );
}

function assertBlobCredentials(): void {
  if (!hasBlobCredentials()) {
    throw new BlobStorageError(
      'CONFIGURATION',
      'Vercel Blob is not configured. Set BLOB_READ_WRITE_TOKEN, or VERCEL_OIDC_TOKEN together with BLOB_STORE_ID.'
    );
  }
}

function normalizeBlobError(error: unknown, context: string): BlobStorageError {
  const status = readStatus(error);
  const code = status ? statusToErrorCode(status) : messageToErrorCode(getErrorMessage(error));
  const message = getErrorMessage(error);

  return new BlobStorageError(code, `${context} failed: ${message}`, error);
}

function readStatus(error: unknown): number | undefined {
  if (typeof error !== 'object' || error === null) {
    return undefined;
  }

  const record = error as Record<string, unknown>;

  if (typeof record.status === 'number') {
    return record.status;
  }

  if (typeof record.statusCode === 'number') {
    return record.statusCode;
  }

  return undefined;
}

function statusToErrorCode(status: number): BlobStorageErrorCode {
  if (status === 404) {
    return 'NOT_FOUND';
  }

  if (status === 429) {
    return 'RATE_LIMIT';
  }

  if (status === 401 || status === 403) {
    return 'FORBIDDEN';
  }

  if (status === 409 || status === 412) {
    return 'CONFLICT';
  }

  if (status === 507) {
    return 'STORAGE_FULL';
  }

  return 'NETWORK';
}

function messageToErrorCode(message: string): BlobStorageErrorCode {
  const normalized = message.toLowerCase();

  if (normalized.includes('not found') || normalized.includes('404')) {
    return 'NOT_FOUND';
  }

  if (
    normalized.includes('no blob credentials') ||
    normalized.includes('no read-write token') ||
    normalized.includes('blob_read_write_token')
  ) {
    return 'CONFIGURATION';
  }

  if (normalized.includes('forbidden') || normalized.includes('unauthorized') || normalized.includes('403')) {
    return 'FORBIDDEN';
  }

  if (normalized.includes('conflict') || normalized.includes('409') || normalized.includes('412')) {
    return 'CONFLICT';
  }

  if (normalized.includes('rate') || normalized.includes('429')) {
    return 'RATE_LIMIT';
  }

  if (normalized.includes('storage') || normalized.includes('quota') || normalized.includes('507')) {
    return 'STORAGE_FULL';
  }

  return 'NETWORK';
}

function getErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}
