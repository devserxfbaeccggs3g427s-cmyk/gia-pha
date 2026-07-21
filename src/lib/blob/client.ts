import { createHash } from 'node:crypto';
import { getSupabaseStorage, getSupabaseStorageBucket } from '@/lib/supabase/server-storage';

export type BlobStorageErrorCode =
  | 'CONFIGURATION'
  | 'NETWORK'
  | 'RATE_LIMIT'
  | 'STORAGE_FULL'
  | 'NOT_FOUND'
  | 'FORBIDDEN'
  | 'CONFLICT';

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
  compositeConfig: (treeId: string) => `data/trees/${treeId}/composite-config.json`,
  compositePublishedConfig: (treeId: string) => `data/trees/${treeId}/composite-published-config.json`,
  compositeMutationPrefix: (treeId: string) => `data/trees/${treeId}/composite-mutations/`,
  compositeMutation: (treeId: string, mutationId: string) => `data/trees/${treeId}/composite-mutations/${mutationId}.json`,
  compositeChangeLogs: (treeId: string) => `data/trees/${treeId}/composite-change-logs.json`,
  compositeManifestPrefix: (treeId: string) => `cache/trees/${treeId}/resolved/`,
  compositeManifest: (treeId: string, audienceHash: string) =>
    `cache/trees/${treeId}/resolved/${audienceHash}.json`
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

export interface BinaryBlobContent {
  stream: ReadableStream<Uint8Array>;
  contentType: string;
  size: number;
  etag: string;
}

export async function withBlobErrorHandling<T>(
  operation: () => Promise<T>,
  context = 'Supabase Storage operation'
): Promise<T> {
  try {
    return await operation();
  } catch (error) {
    if (error instanceof BlobStorageError) throw error;
    throw normalizeBlobError(error, context);
  }
}

export async function readBlob<T>(path: string): Promise<T | null> {
  return withBlobErrorHandling(async () => {
    assertBlobCredentials();
    const { data, error } = await getSupabaseStorage().download(normalizePath(path));
    if (error) {
      if (isNotFound(error)) return null;
      throw error;
    }
    return JSON.parse(await data.text()) as T;
  }, `Read blob "${path}"`);
}

export async function readBinaryBlob(reference: string): Promise<BinaryBlobContent | null> {
  return withBlobErrorHandling(async () => {
    assertBlobCredentials();
    const path = normalizePath(reference);
    const { data, error } = await getSupabaseStorage().download(path);
    if (error) {
      if (isNotFound(error)) return null;
      throw error;
    }
    const bytes = new Uint8Array(await data.arrayBuffer());
    return {
      stream: new Response(bytes).body!,
      contentType: data.type || 'application/octet-stream',
      size: bytes.byteLength,
      etag: `"${createHash('sha256').update(bytes).digest('hex')}"`
    };
  }, `Read binary blob "${reference}"`);
}

export async function writeBlob<T>(path: string, data: T): Promise<void> {
  await writeJsonBlob(path, data, true);
}

export async function writeImmutableBlob<T>(path: string, data: T): Promise<void> {
  await writeJsonBlob(path, data, false);
}

async function writeJsonBlob<T>(path: string, data: T, upsert: boolean): Promise<void> {
  await withBlobErrorHandling(async () => {
    assertBlobCredentials();
    const { error } = await getSupabaseStorage().upload(path, JSON.stringify(data, null, 2), {
      upsert,
      contentType: 'application/json; charset=utf-8',
      cacheControl: '60'
    });
    if (error) throw error;
  }, `Write blob "${path}"`);
}

export async function listBlobs(prefix: string): Promise<BlobMetadata[]> {
  return withBlobErrorHandling(async () => {
    assertBlobCredentials();
    const folder = prefix.endsWith('/') ? prefix.slice(0, -1) : prefix.split('/').slice(0, -1).join('/');
    const namePrefix = prefix.endsWith('/') ? '' : prefix.split('/').at(-1) ?? '';
    const blobs: BlobMetadata[] = [];
    let offset = 0;

    while (true) {
      const { data, error } = await getSupabaseStorage().list(folder, {
        limit: 1000,
        offset,
        sortBy: { column: 'name', order: 'asc' }
      });
      if (error) throw error;
      const files = data.filter((item) => item.id && item.name.startsWith(namePrefix));
      blobs.push(...files.map((item) => {
        const pathname = folder ? `${folder}/${item.name}` : item.name;
        const metadata = item.metadata as Record<string, unknown> | null;
        return {
          pathname,
          url: createStorageReference(pathname),
          size: typeof metadata?.size === 'number' ? metadata.size : 0,
          uploadedAt: new Date(item.updated_at ?? item.created_at ?? 0),
          contentType: typeof metadata?.mimetype === 'string' ? metadata.mimetype : undefined
        };
      }));
      if (data.length < 1000) break;
      offset += data.length;
    }

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
    const { error } = await getSupabaseStorage().upload(path, body, {
      upsert: false,
      cacheControl: '31536000',
      contentType
    });
    if (error) throw error;
    return { url: createStorageReference(path), pathname: path };
  }, `Write binary blob "${path}"`);
}

export async function deleteBlob(path: string): Promise<void> {
  await deleteBlobs([path]);
}

export async function deleteBlobs(paths: readonly string[]): Promise<void> {
  if (paths.length === 0) return;
  await withBlobErrorHandling(async () => {
    assertBlobCredentials();
    const { error } = await getSupabaseStorage().remove(paths.map(normalizePath));
    if (error) throw error;
  }, `Delete ${paths.length} blobs`);
}

export function hasBlobCredentials(): boolean {
  return Boolean(
    (process.env.SUPABASE_URL || process.env.NEXT_PUBLIC_SUPABASE_URL) &&
      process.env.SUPABASE_SERVICE_ROLE_KEY
  );
}

function assertBlobCredentials(): void {
  if (!hasBlobCredentials()) {
    throw new BlobStorageError(
      'CONFIGURATION',
      'Supabase Storage is not configured. Set SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY.'
    );
  }
}

function createStorageReference(path: string): string {
  return `supabase://${getSupabaseStorageBucket()}/${path}`;
}

function normalizePath(reference: string): string {
  if (!reference.startsWith('supabase://')) return reference.replace(/^\/+/, '');
  const value = reference.slice('supabase://'.length);
  const separator = value.indexOf('/');
  if (separator < 0 || value.slice(0, separator) !== getSupabaseStorageBucket()) {
    throw new BlobStorageError('CONFIGURATION', `Invalid Supabase Storage reference "${reference}"`);
  }
  return value.slice(separator + 1);
}

function normalizeBlobError(error: unknown, context: string): BlobStorageError {
  const status = readStatus(error);
  const message = getErrorMessage(error);
  const code = status ? statusToErrorCode(status) : messageToErrorCode(message);
  return new BlobStorageError(code, `${context} failed: ${message}`, error);
}

function readStatus(error: unknown): number | undefined {
  if (typeof error !== 'object' || error === null) return undefined;
  const record = error as Record<string, unknown>;
  const value = record.status ?? record.statusCode;
  if (typeof value === 'number') return value;
  if (typeof value === 'string' && /^\d+$/.test(value)) return Number(value);
  return undefined;
}

function statusToErrorCode(status: number): BlobStorageErrorCode {
  if (status === 404) return 'NOT_FOUND';
  if (status === 429) return 'RATE_LIMIT';
  if (status === 401 || status === 403) return 'FORBIDDEN';
  if (status === 409 || status === 412) return 'CONFLICT';
  if (status === 507) return 'STORAGE_FULL';
  return 'NETWORK';
}

function messageToErrorCode(message: string): BlobStorageErrorCode {
  const normalized = message.toLowerCase();
  if (normalized.includes('not found') || normalized.includes('404')) return 'NOT_FOUND';
  if (normalized.includes('not configured') || normalized.includes('supabase_url') || normalized.includes('service_role')) return 'CONFIGURATION';
  if (normalized.includes('forbidden') || normalized.includes('unauthorized') || normalized.includes('jwt') || normalized.includes('403')) return 'FORBIDDEN';
  if (normalized.includes('duplicate') || normalized.includes('already exists') || normalized.includes('conflict') || normalized.includes('409') || normalized.includes('412')) return 'CONFLICT';
  if (normalized.includes('rate') || normalized.includes('429')) return 'RATE_LIMIT';
  if (normalized.includes('quota') || normalized.includes('storage limit') || normalized.includes('507')) return 'STORAGE_FULL';
  return 'NETWORK';
}

function isNotFound(error: unknown): boolean {
  return readStatus(error) === 404 || messageToErrorCode(getErrorMessage(error)) === 'NOT_FOUND';
}

function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
