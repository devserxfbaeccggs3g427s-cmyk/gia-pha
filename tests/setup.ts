import { beforeEach, vi } from 'vitest';
import { mockBlobStorage } from './utils/mock-blob-storage';

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
