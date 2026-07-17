import { list, put } from '@vercel/blob';

export type BlobStorageErrorCode = 'NETWORK' | 'RATE_LIMIT' | 'STORAGE_FULL' | 'NOT_FOUND';

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
  changeLogs: (treeId: string) => `data/trees/${treeId}/change-logs.json`,
  backup: (treeId: string, timestamp: string) => `backups/${treeId}/${timestamp}.json`
} as const;

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
    const { blobs } = await list({ prefix: path });
    const blob = blobs.find((candidate) => candidate.pathname === path);

    if (!blob) {
      return null;
    }

    const response = await fetch(blob.url);

    if (response.status === 404) {
      return null;
    }

    if (!response.ok) {
      throw new BlobStorageError(
        statusToErrorCode(response.status),
        `Failed to read blob "${path}" with status ${response.status}`
      );
    }

    return (await response.json()) as T;
  }, `Read blob "${path}"`);
}

export async function writeBlob<T>(path: string, data: T): Promise<void> {
  await withBlobErrorHandling(async () => {
    await put(path, JSON.stringify(data, null, 2), {
      access: 'public',
      addRandomSuffix: false,
      contentType: 'application/json; charset=utf-8'
    });
  }, `Write blob "${path}"`);
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
