import { describe, expect, it } from 'vitest';
import { NextRequest, NextResponse } from 'next/server';
import { isPrivateMediaUrl, privateMediaLoader } from '@/lib/images/media-loader';
import { setCacheHeaders } from '@/middleware';

describe('performance cache policies', () => {
  it('always revalidates read-only API responses before offline fallback', () => {
    const response = NextResponse.next();
    setCacheHeaders(new NextRequest('https://example.test/api/trees/tree-1/members'), response);

    expect(response.headers.get('cache-control')).toBe('private, no-store');
    expect(response.headers.get('vary')).toContain('Cookie');
    expect(response.headers.get('vary')).toContain('Authorization');
  });

  it('never caches mutations, authentication, exports, or signed shares', () => {
    for (const [url, method] of [
      ['https://example.test/api/trees/tree-1/members', 'POST'],
      ['https://example.test/api/auth/session', 'GET'],
      ['https://example.test/api/export/tree-1/pdf', 'GET'],
      ['https://example.test/api/share/signed-token', 'GET']
    ] as const) {
      const response = NextResponse.next();
      setCacheHeaders(new NextRequest(url, { method }), response);
      expect(response.headers.get('cache-control')).toBe('private, no-store');
    }
  });

  it('keeps rendered pages user-private and revalidated', () => {
    const response = NextResponse.next();
    setCacheHeaders(new NextRequest('https://example.test/vi/trees'), response);

    expect(response.headers.get('cache-control')).toBe('private, no-cache, must-revalidate');
    expect(response.headers.get('vary')).toContain('Cookie');
  });
});

describe('private responsive image loader', () => {
  it('creates authenticated WebP variants that preserve existing parameters', () => {
    const source = '/api/media/photo-1/content?treeId=tree-1&thumbnail=true';

    expect(isPrivateMediaUrl(source)).toBe(true);
    expect(privateMediaLoader({ src: source, width: 480, quality: 82 })).toBe(
      `${source}&width=480&format=webp&quality=82`
    );
  });

  it('does not classify external images as private media endpoints', () => {
    expect(isPrivateMediaUrl('https://images.example.test/avatar.jpg')).toBe(false);
  });
});
