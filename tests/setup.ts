import { beforeEach, vi } from 'vitest';
import { mockBlobStorage } from './utils/mock-blob-storage';

vi.stubEnv('BLOB_READ_WRITE_TOKEN', 'test-blob-token');

vi.mock('@vercel/blob', () => ({
  list: vi.fn(async ({ prefix }: { prefix?: string } = {}) => ({
    blobs: mockBlobStorage.list(prefix).map((blob) => ({
      pathname: blob.pathname,
      url: blob.url,
      downloadUrl: blob.url,
      size: blob.body.length,
      uploadedAt: blob.uploadedAt,
      contentType: blob.contentType
    }))
  })),
  put: vi.fn(
    async (
      pathname: string,
      body: string,
      options?: {
        contentType?: string;
      }
    ) => mockBlobStorage.put(pathname, body, options?.contentType)
  ),
  get: vi.fn(async (pathname: string) => {
    const blob = mockBlobStorage.get(pathname);

    if (!blob) {
      return null;
    }

    return {
      statusCode: 200,
      stream: new Response(blob.body).body,
      headers: new Headers({ 'content-type': blob.contentType }),
      blob: {
        url: blob.url,
        downloadUrl: blob.url,
        pathname: blob.pathname,
        contentType: blob.contentType,
        size: blob.body.length,
        uploadedAt: blob.uploadedAt,
        etag: 'mock-etag'
      }
    };
  }),
  del: vi.fn(async (pathname: string) => {
    mockBlobStorage.delete(pathname);
  }),
  head: vi.fn(async (pathname: string) => mockBlobStorage.head(pathname))
}));

vi.stubGlobal(
  'fetch',
  vi.fn(async (input: string | URL | Request) => {
    const url = input instanceof Request ? input.url : input.toString();
    const blob = mockBlobStorage.getByUrl(url);

    if (!blob) {
      return new Response(null, { status: 404 });
    }

    return new Response(blob.body, {
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
