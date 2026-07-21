import { beforeEach, vi } from 'vitest';
import { mockBlobStorage } from './utils/mock-blob-storage';

vi.stubEnv('SUPABASE_URL', 'https://supabase.test');
vi.stubEnv('SUPABASE_SERVICE_ROLE_KEY', 'test-service-role-key');
vi.stubEnv('SUPABASE_STORAGE_BUCKET', 'genealogy');

const storageBucket = {
  download: vi.fn(async (pathname: string) => {
    const record = mockBlobStorage.get(pathname);
    return record
      ? { data: new Blob([record.body as BlobPart], { type: record.contentType }), error: null }
      : { data: null, error: { statusCode: '404', message: 'Object not found' } };
  }),
  upload: vi.fn(async (
    pathname: string,
    body: string | Buffer | ArrayBuffer | Blob,
    options?: { contentType?: string; upsert?: boolean }
  ) => {
    if (!options?.upsert && mockBlobStorage.get(pathname)) {
      return { data: null, error: { statusCode: '409', message: 'The resource already exists' } };
    }
    const normalized = typeof body === 'string'
      ? body
      : body instanceof Blob
        ? new Uint8Array(await body.arrayBuffer())
        : body instanceof ArrayBuffer
          ? new Uint8Array(body)
          : new Uint8Array(body);
    mockBlobStorage.put(pathname, normalized, options?.contentType);
    return { data: { path: pathname }, error: null };
  }),
  list: vi.fn(async (folder: string, options?: { limit?: number; offset?: number }) => {
    const prefix = folder ? `${folder}/` : '';
    const records = mockBlobStorage.list(prefix).map((record) => ({
      id: record.pathname,
      name: record.pathname.slice(prefix.length),
      created_at: record.uploadedAt.toISOString(),
      updated_at: record.uploadedAt.toISOString(),
      metadata: { size: record.body.length, mimetype: record.contentType }
    }));
    const offset = options?.offset ?? 0;
    return { data: records.slice(offset, offset + (options?.limit ?? 100)), error: null };
  }),
  remove: vi.fn(async (paths: string[]) => {
    paths.forEach((path) => mockBlobStorage.delete(path));
    return { data: [], error: null };
  })
};

vi.mock('@/lib/supabase/server-storage', () => ({
  getSupabaseStorage: () => storageBucket,
  getSupabaseStorageBucket: () => 'genealogy'
}));

vi.stubGlobal(
  'fetch',
  vi.fn(async (input: string | URL | Request) => {
    const url = input instanceof Request ? input.url : input.toString();
    const blob = mockBlobStorage.getByUrl(url);

    if (!blob) {
      return new Response(null, { status: 404 });
    }

    return new Response(blob.body as unknown as BodyInit, {
      status: 200,
      headers: {
        'content-type': blob.contentType
      }
    });
  })
);

beforeEach(() => {
  mockBlobStorage.clear();
});
